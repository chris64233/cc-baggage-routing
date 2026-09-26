package com.chris64233.baggagerouting.api;

import java.util.List;

/** 计划路线查询结果：按世代顺序展示全部历史路线与当前有效路线。 */
public record RoutePlanResponse(
        String tag,
        int activeGenerationNo,
        List<GenerationView> generations) {
}
