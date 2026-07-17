package com.larfchan.giffgaffkeepalive;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.io.Closeable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

final class CellularUdpSender implements Closeable {
    interface Callback {
        void onProgress(String message);

        void onSuccess(Result result);

        void onError(String message);
    }

    static final class Result {
        final InetAddress destination;
        final int port;
        final boolean usedDns;
        final boolean roaming;
        final int theoreticalIpBytes;
        final long uidTxDelta;
        final long uidRxDelta;

        Result(
                InetAddress destination,
                int port,
                boolean usedDns,
                boolean roaming,
                int theoreticalIpBytes,
                long uidTxDelta,
                long uidRxDelta
        ) {
            this.destination = destination;
            this.port = port;
            this.usedDns = usedDns;
            this.roaming = roaming;
            this.theoreticalIpBytes = theoreticalIpBytes;
            this.uidTxDelta = uidTxDelta;
            this.uidRxDelta = uidRxDelta;
        }
    }

    private static final int TRAFFIC_TAG = 0x474b;
    private static final long OVERALL_TIMEOUT_MS = 25_000L;

    private final ConnectivityManager connectivityManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile SendSession activeSession;
    private volatile boolean closed;

    CellularUdpSender(Context context) {
        this.connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    void send(TargetSpec target, boolean requireRoaming, Callback callback) {
        if (closed) {
            callback.onError("发送器已经关闭，请重新打开应用");
            return;
        }
        if (connectivityManager == null) {
            callback.onError("此设备没有可用的网络管理服务");
            return;
        }
        if (activeSession != null) {
            callback.onError("已有一次发送正在进行");
            return;
        }

        SendSession session = new SendSession(target, requireRoaming, callback);
        activeSession = session;
        mainHandler.postDelayed(session.timeoutRunnable, OVERALL_TIMEOUT_MS);

        postProgress(session, "正在申请系统首选的非 VPN 蜂窝网络…");
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();

        ConnectivityManager.NetworkCallback networkCallback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        if (session.finished.get()) {
                            return;
                        }
                        postProgress(session, "已发现蜂窝网络，正在确认网络能力…");
                    }

                    @Override
                    public void onCapabilitiesChanged(
                            Network network,
                            NetworkCapabilities capabilities
                    ) {
                        if (session.finished.get()
                                || !isExpectedCellularCapabilities(capabilities)
                                || !session.networkClaimed.compareAndSet(false, true)
                        ) {
                            return;
                        }
                        postProgress(session, "已确认非 VPN 蜂窝网络，准备提交负载…");
                        executeSend(session, network);
                    }

                    @Override
                    public void onUnavailable() {
                        if (session.networkClaimed.compareAndSet(false, true)) {
                            session.fail("无法取得蜂窝网络。请检查默认数据 SIM、移动数据和数据漫游设置。");
                        }
                    }
                };

        session.networkCallback = networkCallback;
        session.callbackRegistered = true;
        try {
            connectivityManager.requestNetwork(request, networkCallback);
        } catch (SecurityException exception) {
            session.callbackRegistered = false;
            session.fail("系统不允许本应用申请蜂窝网络，请检查应用联网限制");
        } catch (RuntimeException exception) {
            session.callbackRegistered = false;
            session.fail("申请蜂窝网络失败：" + safeMessage(exception));
        }
    }

    private void executeSend(SendSession session, Network network) {
        if (closed || session.finished.get()) {
            session.cancelSilently();
            return;
        }
        try {
            executor.execute(() -> sendOnNetwork(session, network));
        } catch (RejectedExecutionException exception) {
            session.cancelSilently();
        }
    }

    private boolean isExpectedCellularNetwork(Network network) {
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(network);
        return isExpectedCellularCapabilities(capabilities);
    }

    private static boolean isExpectedCellularCapabilities(
            NetworkCapabilities capabilities
    ) {
        return capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }

    private void sendOnNetwork(SendSession session, Network network) {
        if (session.finished.get()) {
            return;
        }
        if (!isExpectedCellularNetwork(network)) {
            session.fail("系统提供的网络不是预期的非 VPN 蜂窝网络，因此未提交负载。");
            return;
        }

        Boolean roaming = readRoamingState(network);
        if (session.requireRoaming && !Boolean.TRUE.equals(roaming)) {
            String detail = roaming == null
                    ? "系统无法确认当前蜂窝网络是否处于漫游"
                    : "系统报告当前蜂窝网络不是漫游网络";
            session.fail(detail + "，因此未发送。可在确认设置无误后关闭“仅漫游时发送”。");
            return;
        }

        TrafficStats.setThreadStatsTag(TRAFFIC_TAG);
        long txBefore = readUidBytes(true);
        long rxBefore = readUidBytes(false);

        try {
            InetAddress destination = session.target.numericAddress;
            if (destination == null) {
                postProgress(session, "正在通过蜂窝网络解析主机名（会产生 DNS 流量）…");
                InetAddress[] addresses = network.getAllByName(session.target.host);
                destination = AddressPolicy.selectPublicInternetAddress(addresses);
            }

            if (session.finished.get()) {
                return;
            }

            postProgress(session, "正在提交含 1 字节应用负载的 UDP 数据报…");
            try (DatagramSocket socket = new DatagramSocket()) {
                network.bindSocket(socket);
                socket.connect(destination, session.target.port);
                if (!session.beginIrreversibleSend()) {
                    return;
                }
                byte[] payload = new byte[]{0x47};
                socket.send(new DatagramPacket(payload, payload.length));
            }

            long txAfter = readUidBytes(true);
            long rxAfter = readUidBytes(false);
            int theoreticalBytes = destination instanceof Inet4Address ? 29 : 49;

            session.succeed(new Result(
                    destination,
                    session.target.port,
                    session.target.usesDns(),
                    Boolean.TRUE.equals(roaming),
                    theoreticalBytes,
                    delta(txBefore, txAfter),
                    delta(rxBefore, rxAfter)
            ));
        } catch (IllegalArgumentException exception) {
            session.fail(exception.getMessage());
        } catch (Exception exception) {
            session.fail("未能发送：" + safeMessage(exception));
        } finally {
            TrafficStats.clearThreadStatsTag();
        }
    }

    private Boolean readRoamingState(Network network) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                NetworkCapabilities capabilities =
                        connectivityManager.getNetworkCapabilities(network);
                if (capabilities == null) {
                    return null;
                }
                return !capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
            }
            @SuppressWarnings("deprecation")
            android.net.NetworkInfo networkInfo =
                    connectivityManager.getNetworkInfo(network);
            return networkInfo == null ? null : networkInfo.isRoaming();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static long readUidBytes(boolean transmitted) {
        long value = transmitted
                ? TrafficStats.getUidTxBytes(Process.myUid())
                : TrafficStats.getUidRxBytes(Process.myUid());
        return value == TrafficStats.UNSUPPORTED ? -1L : value;
    }

    private static long delta(long before, long after) {
        return before >= 0L && after >= before ? after - before : -1L;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private void postProgress(SendSession expectedSession, String message) {
        mainHandler.post(() -> {
            SendSession session = activeSession;
            if (!closed && session == expectedSession && !session.finished.get()) {
                session.callback.onProgress(message);
            }
        });
    }

    @Override
    public void close() {
        closed = true;
        SendSession session = activeSession;
        if (session != null) {
            session.cancelSilently();
        }
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private final class SendSession {
        final TargetSpec target;
        final boolean requireRoaming;
        final Callback callback;
        final AtomicBoolean networkClaimed = new AtomicBoolean(false);
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable timeoutRunnable =
                this::timeout;

        volatile ConnectivityManager.NetworkCallback networkCallback;
        volatile boolean callbackRegistered;
        private boolean sendStarted;

        SendSession(TargetSpec target, boolean requireRoaming, Callback callback) {
            this.target = target;
            this.requireRoaming = requireRoaming;
            this.callback = callback;
        }

        void succeed(Result result) {
            finish(() -> callback.onSuccess(result));
        }

        void fail(String message) {
            finish(() -> callback.onError(message));
        }

        void cancelSilently() {
            synchronized (this) {
                if (sendStarted) {
                    return;
                }
                finish(null);
            }
        }

        boolean beginIrreversibleSend() {
            synchronized (this) {
                if (finished.get()) {
                    return false;
                }
                sendStarted = true;
                mainHandler.removeCallbacks(timeoutRunnable);
                return true;
            }
        }

        private void timeout() {
            synchronized (this) {
                if (sendStarted || finished.get()) {
                    return;
                }
                fail("操作超时，1 字节应用负载未提交。请检查默认数据 SIM、移动数据和漫游设置。");
            }
        }

        private void finish(Runnable completion) {
            synchronized (this) {
                if (!finished.compareAndSet(false, true)) {
                    return;
                }
                mainHandler.removeCallbacks(timeoutRunnable);
                if (callbackRegistered && networkCallback != null) {
                    try {
                        connectivityManager.unregisterNetworkCallback(networkCallback);
                    } catch (RuntimeException ignored) {
                        // The system may already have released a timed-out request.
                    }
                }
                if (activeSession == this) {
                    activeSession = null;
                }
            }
            if (completion != null) {
                mainHandler.post(() -> {
                    if (!closed) {
                        completion.run();
                    }
                });
            }
        }
    }
}
