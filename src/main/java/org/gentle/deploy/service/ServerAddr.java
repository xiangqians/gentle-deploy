package org.gentle.deploy.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author xiangqian
 * @date 12:33 2022/09/25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerAddr {

    private String host;
    private int port;
    private String path;

}

