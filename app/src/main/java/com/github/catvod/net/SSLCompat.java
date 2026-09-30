package com.github.catvod.net;

import android.annotation.SuppressLint;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * TLS 兼容工厂：在老设备（Android 5/6）上强制启用 TLSv1.2 与现代密码套件。
 * 安全默认：证书校验走系统信任库，不再全局安装 trust-all。
 * trust-all 仅作为按源 opt-in 的兼容开关保留（createTrustAll / TRUST_ALL_VERIFIER），
 * 不得设置为进程默认。
 */
public class SSLCompat extends SSLSocketFactory {

    /** 安全默认：系统默认主机名校验（不再无条件放行）。 */
    public static final HostnameVerifier VERIFIER = HttpsURLConnection.getDefaultHostnameVerifier();
    /** 按源 opt-in：仅对明确声明"自签名证书"的源使用。 */
    public static final HostnameVerifier TRUST_ALL_VERIFIER = (hostname, session) -> true;

    private static String[] cipherSuites;
    private static String[] protocols;
    private final SSLSocketFactory factory;

    static {
        try {
            SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            List<String> protocols = new LinkedList<>();
            for (String protocol : socket.getSupportedProtocols()) if (!protocol.toUpperCase().contains("SSL")) protocols.add(protocol);
            SSLCompat.protocols = protocols.toArray(new String[protocols.size()]);
            List<String> allowedCiphers = Arrays.asList("TLS_RSA_WITH_AES_256_GCM_SHA384", "TLS_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECHDE_RSA_WITH_AES_128_GCM_SHA256", "TLS_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA");
            List<String> availableCiphers = Arrays.asList(socket.getSupportedCipherSuites());
            HashSet<String> preferredCiphers = new HashSet<>(allowedCiphers);
            preferredCiphers.retainAll(availableCiphers);
            preferredCiphers.addAll(new HashSet<>(Arrays.asList(socket.getEnabledCipherSuites())));
            SSLCompat.cipherSuites = preferredCiphers.toArray(new String[preferredCiphers.size()]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private SSLCompat(SSLSocketFactory factory) {
        this.factory = factory;
    }

    /** 安全默认工厂：系统信任库校验 + TLS 协议升级。 */
    public static SSLCompat create() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            return new SSLCompat(context.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
            return new SSLCompat((SSLSocketFactory) SSLSocketFactory.getDefault());
        }
    }

    /** 按源 opt-in 的 trust-all 兼容工厂，禁止全局默认使用。 */
    @SuppressLint({"TrustAllX509TrustManager", "CustomX509TrustManager"})
    public static SSLCompat createTrustAll() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[]{TM}, null);
            return new SSLCompat(context.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
            return create();
        }
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return cipherSuites;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return cipherSuites;
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        Socket ssl = factory.createSocket(s, host, port, autoClose);
        if (ssl instanceof SSLSocket) upgradeTLS((SSLSocket) ssl);
        return ssl;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket ssl = factory.createSocket(host, port);
        if (ssl instanceof SSLSocket) upgradeTLS((SSLSocket) ssl);
        return ssl;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        Socket ssl = factory.createSocket(host, port, localHost, localPort);
        if (ssl instanceof SSLSocket) upgradeTLS((SSLSocket) ssl);
        return ssl;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        Socket ssl = factory.createSocket(host, port);
        if (ssl instanceof SSLSocket) upgradeTLS((SSLSocket) ssl);
        return ssl;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Socket ssl = factory.createSocket(address, port, localAddress, localPort);
        if (ssl instanceof SSLSocket) upgradeTLS((SSLSocket) ssl);
        return ssl;
    }

    private void upgradeTLS(SSLSocket ssl) {
        if (protocols != null) ssl.setEnabledProtocols(protocols);
        if (cipherSuites != null) ssl.setEnabledCipherSuites(cipherSuites);
    }

    /**
     * 安全默认 TrustManager：委托系统信任库逐条校验证书链。
     */
    public static final X509TrustManager TM = new X509TrustManager() {

        private final X509TrustManager delegate = systemTrustManager();

        private X509TrustManager systemTrustManager() {
            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null);
                for (javax.net.ssl.TrustManager tm : tmf.getTrustManagers()) {
                    if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (delegate != null) delegate.checkClientTrusted(chain, authType);
            else throw new CertificateException("No system TrustManager available");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (delegate != null) delegate.checkServerTrusted(chain, authType);
            else throw new CertificateException("No system TrustManager available");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate != null ? delegate.getAcceptedIssuers() : new X509Certificate[]{};
        }
    };
}
