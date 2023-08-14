package com.hmdp.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 朱焕杰
 * @version 1.0
 * @description TODO
 * @date 2023/6/24 10:53
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Person {
    private String name;
    private String sex;
    private Integer age;
}
