package com.milind.employeemcp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.milind.employeemcp.domain.EmployeeService.SearchCriteria;

import org.junit.jupiter.api.Test;

class EmployeeServiceTest {

    private final EmployeeService service = new EmployeeService(new EmployeeRepository());

    private static SearchCriteria country(String c) {
        return new SearchCriteria(null, null, null, null, null, c, null);
    }

    @Test
    void getByIdIsCaseInsensitive() {
        assertThat(service.getById("e001")).map(Employee::name).contains("Priya Sharma");
    }

    @Test
    void filtersByCountry() {
        assertThat(service.search(country("India"))).extracting(Employee::employeeId).containsExactly("E001", "E002");
    }

    @Test
    void combinesFiltersWithAnd() {
        var c = new SearchCriteria(null, LocalDate.of(1990, 4, 12), null, null, "APAC", null, null);
        assertThat(service.search(c)).extracting(Employee::employeeId).containsExactly("E001");
    }

    @Test
    void dobRangeIsInclusive() {
        var c = new SearchCriteria(null, null, LocalDate.of(1985, 11, 3), LocalDate.of(1988, 1, 30), null, null, null);
        assertThat(service.search(c)).extracting(Employee::employeeId).containsExactly("E002", "E004", "E006");
    }

    @Test
    void noFilterIsRejected() {
        assertThatThrownBy(() -> service.search(new SearchCriteria(null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invertedRangeIsRejected() {
        var c = new SearchCriteria(null, null, LocalDate.of(2000, 1, 1), LocalDate.of(1990, 1, 1), null, null, null);
        assertThatThrownBy(() -> service.search(c)).isInstanceOf(IllegalArgumentException.class);
    }
}
