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

    @Test
    void permitsOneSelectWithATrailingSemicolonButRejectsReadBypassSyntax() {
        ReadOnlySqlValidator.ValidatedSql result = validator.validate(
                "select sku from inventory where warehouse_id = :warehouseId;");

        assertThat(result.sql()).isEqualTo("select sku from inventory where warehouse_id = :warehouseId");
        assertThatThrownBy(() -> validator.validate("select * from inventory for update"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate("select * from inventory into outfile '/tmp/export.csv'"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate("with inventory as (select * from stock) select * from inventory"))
                .isInstanceOf(BusinessException.class);
    }
}
