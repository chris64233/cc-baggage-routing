package com.chris64233.baggagerouting.domain;

/** 路线世代状态：改签或误装会以新世代接续，旧世代被取代但保留留痕。 */
public enum GenerationStatus {
    /** 当前有效路线。 */
    ACTIVE,
    /** 改签后被新世代取代。 */
    SUPERSEDED,
    /** 因误装异常，从发现点起整段路线失效。 */
    ABORTED
}
