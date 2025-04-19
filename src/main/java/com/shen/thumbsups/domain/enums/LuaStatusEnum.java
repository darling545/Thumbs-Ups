package com.shen.thumbsups.domain.enums;


import lombok.Getter;

/**
 *
 * 点赞类型枚举
 */
@Getter
public enum LuaStatusEnum {


    SUCCESS(1L),
    FAIL(-1L),
    ;

    private final long value;

    LuaStatusEnum(long value) {
        this.value = value;
    }
}
