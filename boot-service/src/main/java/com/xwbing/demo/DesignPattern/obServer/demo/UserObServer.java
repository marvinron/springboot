package com.xwbing.demo.DesignPattern.obServer.demo;

/**
 * @author xiangwb
 * @date 2020/3/6 21:57
 */
public class UserObServer implements ObServer {
    private String name;

    public UserObServer(String name) {
        this.name = name;

    }

    @Override
    public void update(String message) {
        System.out.println(name + ",收到微信服务消息:" + message);
    }
}
