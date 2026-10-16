package com.milind.employeemcp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

/** In-memory seed data. Replace with a real store without touching the tool layer. */
@Repository
public class EmployeeRepository {

    private final List<Employee> employees = List.of(
            new Employee("E001", "Priya Sharma", LocalDate.of(1990, 4, 12), "APAC", "India", "Maharashtra", new BigDecimal("85000.00")),
            new Employee("E002", "Rahul Verma", LocalDate.of(1985, 11, 3), "APAC", "India", "Karnataka", new BigDecimal("92000.00")),
            new Employee("E003", "Emily Carter", LocalDate.of(1992, 7, 21), "AMER", "USA", "California", new BigDecimal("120000.00")),
            new Employee("E004", "John Miller", LocalDate.of(1988, 1, 30), "AMER", "USA", "Texas", new BigDecimal("110000.00")),
            new Employee("E005", "Sophie Martin", LocalDate.of(1991, 9, 8), "EMEA", "France", "Ile-de-France", new BigDecimal("78000.00")),
            new Employee("E006", "Hans Muller", LocalDate.of(1985, 11, 3), "EMEA", "Germany", "Bavaria", new BigDecimal("88000.00")),
            new Employee("E007", "Aiko Tanaka", LocalDate.of(1994, 2, 14), "APAC", "Japan", "Tokyo", new BigDecimal("95000.00")),
            new Employee("E008", "Carlos Silva", LocalDate.of(1990, 4, 12), "AMER", "Brazil", "Sao Paulo", new BigDecimal("67000.00")));

    public List<Employee> findAll() {
        return employees;
    }

    public Optional<Employee> findById(String employeeId) {
        return employees.stream().filter(e -> e.employeeId().equalsIgnoreCase(employeeId)).findFirst();
    }
}
