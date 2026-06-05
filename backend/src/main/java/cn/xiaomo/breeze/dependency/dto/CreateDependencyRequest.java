package cn.xiaomo.breeze.dependency.dto;

import lombok.Data;

@Data
public class CreateDependencyRequest {
    private Long dependsOnTaskId;
    private String type;
}
