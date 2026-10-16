package com.milind.employeemcp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Employee(
        String employeeId,
        String name,
        LocalDate dateOfBirth,
        String region,
        String country,
        String state,
        BigDecimal salary) {}
