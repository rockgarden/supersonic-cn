package com.tencent.supersonic.headless.api.pojo.request;

import com.tencent.supersonic.common.pojo.enums.StatusEnum;
import com.tencent.supersonic.common.pojo.enums.TypeEnums;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DictItemFilter {
    private Long id;
    private TypeEnums type;
    private Long itemId;
    /** 批量 itemId 过滤；与 itemId 同时传入时取交集 */
    private List<Long> itemIds;
    private StatusEnum status;
}
