package com.learnspark.common.result;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 分页结果封装。
 */
@Data
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int page;
    private int size;
    private int totalPages;

    private PageResult(List<T> list, long total, int page, int size, int totalPages) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = totalPages;
    }

    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.getTotalPages()
        );
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new PageResult<>(list, total, page, size, totalPages);
    }
}
