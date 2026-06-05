package cn.xiaomo.breeze.common;

import java.util.List;

public record PageDTO<T>(
    List<T> items,
    long total,
    int page,
    int size
) {
    public static <T> PageDTO<T> of(List<T> items, long total, int page, int size) {
        return new PageDTO<>(items, total, page, size);
    }
}
