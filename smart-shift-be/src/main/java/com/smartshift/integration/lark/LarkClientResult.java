package com.smartshift.integration.lark;

public record LarkClientResult(
    boolean success,
    Integer httpStatus,
    String responseBody,
    String error
) {
}
