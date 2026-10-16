package com.milind.employeemcp.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class EmployeeService {

    private final EmployeeRepository repository;

    public EmployeeService(EmployeeRepository repository) {
        this.repository = repository;
    }

    public Optional<Employee> getById(String employeeId) {
        return repository.findById(employeeId);
    }

    /**
     * AND-combines every supplied (non-null) filter. At least one filter is required so a
     * call can never silently dump the whole employee table.
     */
    public List<Employee> search(SearchCriteria c) {
        if (c.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one filter is required: employeeId, dateOfBirth, dobFrom, dobTo, region, country or state");
        }
        if (c.dobFrom() != null && c.dobTo() != null && c.dobFrom().isAfter(c.dobTo())) {
            throw new IllegalArgumentException("dobFrom must not be after dobTo");
        }
        return repository.findAll().stream()
                .filter(e -> c.employeeId() == null || e.employeeId().equalsIgnoreCase(c.employeeId()))
                .filter(e -> c.dateOfBirth() == null || e.dateOfBirth().equals(c.dateOfBirth()))
                .filter(e -> c.dobFrom() == null || !e.dateOfBirth().isBefore(c.dobFrom()))
                .filter(e -> c.dobTo() == null || !e.dateOfBirth().isAfter(c.dobTo()))
                .filter(e -> c.region() == null || e.region().equalsIgnoreCase(c.region()))
                .filter(e -> c.country() == null || e.country().equalsIgnoreCase(c.country()))
                .filter(e -> c.state() == null || e.state().equalsIgnoreCase(c.state()))
                .toList();
    }

    public record SearchCriteria(
            String employeeId,
            LocalDate dateOfBirth,
            LocalDate dobFrom,
            LocalDate dobTo,
            String region,
            String country,
            String state) {

        boolean isEmpty() {
            return employeeId == null && dateOfBirth == null && dobFrom == null && dobTo == null
                    && region == null && country == null && state == null;
        }
    }
}
