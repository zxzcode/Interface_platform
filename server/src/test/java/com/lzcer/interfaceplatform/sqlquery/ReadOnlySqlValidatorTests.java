package com.lzcer.interfaceplatform.sqlquery;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadOnlySqlValidatorTests {

    private final ReadOnlySqlValidator validator = new ReadOnlySqlValidator();

    @Test
    void acceptsParameterizedSelectAndExtractsParameters() {
        ReadOnlySqlValidator.ValidatedSql result = validator.validate(
                "select sku, qty from inventory where warehouse_id = :warehouseId and sku = :sku");

        assertThat(result.parameters()).containsExactly("warehouseId", "sku");
    }

    @Test
    void rejectsWriteStatementsCommentsAndMultipleStatements() {
        assertThatThrownBy(() -> validator.validate("update inventory set qty = 0"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate("select * from inventory; delete from inventory"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate("select * from inventory -- bypass"))
                .isInstanceOf(BusinessException.class);
    }
}
