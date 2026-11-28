package com.vedioai.server.common.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException() {
        super("资源不存在");
    }
}