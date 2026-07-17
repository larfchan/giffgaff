package com.larfchan.giffgaffkeepalive;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

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

        void onError(String message, boolean outcomeUnknown);
    }

    static final class Result {
        final InetAddress destination;
        final int port;
        final int applicationBytes;
        final int theoreticalIpBytes;

        Result(
                InetAddress destination,
                int port,
                int applicationBytes,
                int theoreticalIpBytes
        ) {
            this.destination = destination;
            this.port = port;
            this.applicationBytes = applicationBytes;
            this.theoreticalIpBytes = theoreticalIpBytes;
        }
    }

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

    void send(TargetSpec target, Callback callback) {
        if (closed) {
            callback.onError("发送器已经关闭，请重新打开应用", false);
            return;
        }
        if (connectivityManager == null) {
            callback.onError("此设备没有可用的网络管理服务", false);
            return;
        }
        if (activeSession != null) {
            callback.onError("已有一次发送正在进行", false);
            return;
        }

        SendSession session = new SendSession(target, callback);
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
                        postProgress(session, "已确认非 VPN 蜂窝网络，准备提交 DNS 查询…");
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

        try {
            InetAddress destination = session.target.numericAddress;

            byte[] payload = DnsQuery.rootA();
            postProgress(session, "正在提交 " + payload.length + " 字节 DNS 查询…");
            try (DatagramSocket socket = new DatagramSocket()) {
                network.bindSocket(socket);
                socket.connect(destination, session.target.port);
                if (!session.beginIrreversibleSend()) {
                    return;
                }
                socket.send(new DatagramPacket(payload, payload.length));
            }

            int theoreticalBytes = (destination instanceof Inet4Address ? 28 : 48)
                    + payload.length;

            session.succeed(new Result(
                    destination,
                    session.target.port,
                    payload.length,
                    theoreticalBytes
            ));
        } catch (IllegalArgumentException exception) {
            session.fail(exception.getMessage(), session.hasSendStarted());
        } catch (Exception exception) {
            session.fail("未能发送：" + safeMessage(exception), session.hasSendStarted());
        }
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
        final Callback callback;
        final AtomicBoolean networkClaimed = new AtomicBoolean(false);
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable timeoutRunnable =
                this::timeout;

        volatile ConnectivityManager.NetworkCallback networkCallback;
        volatile boolean callbackRegistered;
        private boolean sendStarted;

        SendSession(TargetSpec target, Callback callback) {
            this.target = target;
            this.callback = callback;
        }

        void succeed(Result result) {
            finish(() -> callback.onSuccess(result));
        }

        void fail(String message) {
            fail(message, false);
        }

        void fail(String message, boolean outcomeUnknown) {
            finish(() -> callback.onError(message, outcomeUnknown));
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

        synchronized boolean hasSendStarted() {
            return sendStarted;
        }

        private void timeout() {
            synchronized (this) {
                if (sendStarted || finished.get()) {
                    return;
                }
                fail("操作超时，DNS 查询未提交。请检查默认数据 SIM、移动数据和漫游设置。");
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
