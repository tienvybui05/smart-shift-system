package com.smartshift.integration.lark;

public record LarkUserLookupResult(
    boolean success,
    String openId,
    String error
) {
}
