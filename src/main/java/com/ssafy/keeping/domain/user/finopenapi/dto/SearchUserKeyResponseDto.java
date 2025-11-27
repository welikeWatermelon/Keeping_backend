package com.ssafy.keeping.domain.user.finopenapi.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchUserKeyResponseDto {
    private String userId;
    private String userName;
    private String institutionCode;
    private String modified;
    private String created;
    private String userKey;
}
