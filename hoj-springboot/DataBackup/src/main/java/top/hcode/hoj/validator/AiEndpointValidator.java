package top.hcode.hoj.validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Validates outbound AI provider endpoints before a bearer token is attached.
 */
@Component
public class AiEndpointValidator {

    private final boolean allowHttp;
    private final List<String> allowedHosts;

    public AiEndpointValidator(
            @Value("${ai-assistant-allow-http:false}") boolean allowHttp,
            @Value("${ai-assistant-allowed-hosts:}") String allowedHosts) {
        this.allowHttp = allowHttp;
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    public String validateAndNormalize(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim()).normalize();
            String scheme = uri.getScheme();
            boolean secure = "https".equalsIgnoreCase(scheme);
            boolean explicitlyAllowedHttp =
                    allowHttp && "http".equalsIgnoreCase(scheme);
            if (!secure && !explicitlyAllowedHttp) {
                throw new IllegalArgumentException("AI 服务地址必须使用 HTTPS");
            }
            if (uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("AI 服务地址不能包含用户信息、查询参数或片段");
            }

            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                throw new IllegalArgumentException("AI 服务地址缺少有效主机名");
            }
            host = IDN.toASCII(host).toLowerCase(Locale.ROOT);
            validateAllowedHost(host);
            validateResolvedAddresses(host);

            String normalized = uri.toString();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法验证 AI 服务地址", exception);
        }
    }

    private void validateAllowedHost(String host) {
        if ("localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")) {
            throw new IllegalArgumentException("AI 服务地址不能指向本地或内部主机");
        }
        if (allowedHosts.isEmpty()) {
            return;
        }
        for (String allowedHost : allowedHosts) {
            if (host.equals(allowedHost)
                    || (allowedHost.startsWith("*.")
                    && host.endsWith(allowedHost.substring(1)))) {
                return;
            }
        }
        throw new IllegalArgumentException("AI 服务主机不在允许列表中");
    }

    private void validateResolvedAddresses(String host) throws Exception {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw new IllegalArgumentException("AI 服务主机无法解析");
        }
        for (InetAddress address : addresses) {
            if (isPrivateOrSpecial(address)) {
                throw new IllegalArgumentException("AI 服务地址不能指向私有或特殊网络");
            }
        }
    }

    private boolean isPrivateOrSpecial(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || first >= 224
                    || (first == 100 && second >= 64 && second <= 127);
        }
        return bytes.length == 16 && ((bytes[0] & 0xfe) == 0xfc);
    }
}
