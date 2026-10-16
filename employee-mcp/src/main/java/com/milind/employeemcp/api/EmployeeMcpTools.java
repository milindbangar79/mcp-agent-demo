package com.milind.employeemcp.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.milind.employeemcp.domain.Employee;
import com.milind.employeemcp.domain.EmployeeService;
import com.milind.employeemcp.domain.EmployeeService.SearchCriteria;
import com.milind.mcpratelimit.ToolRateLimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMcpTools {

    private static final Logger log = LoggerFactory.getLogger(EmployeeMcpTools.class);

    private final EmployeeService service;
    private final ToolRateLimiter rateLimiter;

    public EmployeeMcpTools(EmployeeService service, ToolRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @McpTool(
            name = "get_employee_details",
            description = "Fetches the details of ONE employee by employee id (e.g. E001): name, date of birth, "
                    + "region, country, state and salary.")
    public Employee getEmployeeDetails(
            @McpToolParam(description = "Employee id, e.g. E001", required = true) String employeeId) {
        rateLimiter.acquireOrThrow();
        log.info("Tool invoked: get_employee_details");
        return service.getById(blankToNull(employeeId))
                .orElseThrow(() -> new IllegalArgumentException("No employee found with id " + employeeId));
    }

    @McpTool(
            name = "search_employees",
            description = "Dynamic employee search. Supply ANY combination of filters (all optional, combined with AND, "
                    + "at least one required): employee id, exact date of birth, a date-of-birth range, region, "
                    + "country, state. Returns the list of matching employees including salary.")
    public List<Employee> searchEmployees(
            @McpToolParam(description = "Employee id, e.g. E001", required = false) String employeeId,
            @McpToolParam(description = "Exact date of birth, ISO format yyyy-MM-dd", required = false) String dateOfBirth,
            @McpToolParam(description = "Earliest date of birth (inclusive), yyyy-MM-dd", required = false) String dobFrom,
            @McpToolParam(description = "Latest date of birth (inclusive), yyyy-MM-dd", required = false) String dobTo,
            @McpToolParam(description = "Region, e.g. APAC, AMER, EMEA", required = false) String region,
            @McpToolParam(description = "Country, e.g. India", required = false) String country,
            @McpToolParam(description = "State or province, e.g. Karnataka", required = false) String state) {
        rateLimiter.acquireOrThrow();
        log.info("Tool invoked: search_employees");
        return service.search(new SearchCriteria(
                blankToNull(employeeId),
                parseDate(dateOfBirth, "dateOfBirth"),
                parseDate(dobFrom, "dobFrom"),
                parseDate(dobTo, "dobTo"),
                blankToNull(region),
                blankToNull(country),
                blankToNull(state)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate parseDate(String value, String field) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " must be in yyyy-MM-dd format, got: " + value);
        }
    }
}
