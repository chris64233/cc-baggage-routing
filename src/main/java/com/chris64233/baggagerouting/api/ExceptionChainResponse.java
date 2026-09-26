package com.chris64233.baggagerouting.api;

import java.util.List;

/** 异常处理链查询结果（按登记时间顺序）。 */
public record ExceptionChainResponse(
        String tag,
        int caseCount,
        List<ExceptionCaseView> cases) {
}
