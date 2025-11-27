package com.ssafy.keeping.domain.store.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsRequestDto {


    private LocalDate date;

    private LocalDate startDate;

    private LocalDate endDate;
}