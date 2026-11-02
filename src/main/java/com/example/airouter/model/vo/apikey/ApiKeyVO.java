package com.example.airouter.model.vo.apikey;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ApiKeyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String keyValue;

    private String status;

    private Long totalTokens;

    private LocalDateTime lastUsedTime;

    private LocalDateTime createTime;
}
