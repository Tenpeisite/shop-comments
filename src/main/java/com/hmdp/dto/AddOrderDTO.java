package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.convert.DataSizeUnit;

/**
 * @author 朱焕杰
 * @version 1.0
 * @description TODO
 * @date 2023/6/15 17:28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddOrderDTO {
    Long voucherId;
    Long userId;
    Long orderId;
}
