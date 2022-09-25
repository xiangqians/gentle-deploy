package org.gentle.deploy;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * @author xiangqian
 * @date 14:29 2022/09/25
 */
@Slf4j
public class ServerRegisterTest {

    public static void main(String[] args) throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            String reqUrl = "http://localhost:9999/_api/server/register?secret=%s&host=%s&port=%s&path=%s";
            String secret = "3a5f0c4a-3bc7-11ed-911e-0242ac110002";
            String host = null;
            Integer port = 8080;
            String path = null;
            reqUrl = String.format(reqUrl,
                    URLEncoder.encode(secret, StandardCharsets.UTF_8),
                    URLEncoder.encode(Optional.ofNullable(host).orElse(""), StandardCharsets.UTF_8),
                    URLEncoder.encode(Optional.ofNullable(port).map(String::valueOf).orElse(""), StandardCharsets.UTF_8),
                    URLEncoder.encode(Optional.ofNullable(path).orElse(""), StandardCharsets.UTF_8));
            URL url = new URL(reqUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30 * 1000); // ms
            connection.setReadTimeout(30 * 1000); // ms
            connection.connect();
            input = connection.getInputStream();
            log.debug("response: {}", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            if (Objects.nonNull(connection)) {
                connection.disconnect();
            }
            IOUtils.closeQuietly(input);
        }
    }

}
