package com.middleware.alert.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlNormalizerTest {

    @Test
    void replacesLiteralsWithPlaceholders() {
        assertThat(SqlNormalizer.normalize("select * from t_user where id = 123 and name = 'abc'"))
                .isEqualTo("select * from t_user where id = ? and name = ?");
    }

    @Test
    void collapsesParameterLists() {
        assertThat(SqlNormalizer.normalize("select * from t where id in (?, ?, ?)"))
                .isEqualTo("select * from t where id in (?)");
    }

    @Test
    void removesComments() {
        assertThat(SqlNormalizer.normalize("select 1 -- comment\nfrom t /* block */"))
                .isEqualTo("select ? from t");
    }

    @Test
    void compressesWhitespace() {
        assertThat(SqlNormalizer.normalize("select\n\t *   from   t"))
                .isEqualTo("select * from t");
    }

    @Test
    void doesNotTouchIdentifiersContainingDigits() {
        assertThat(SqlNormalizer.normalize("select t1.a from t1 where t1.b = 2"))
                .isEqualTo("select t1.a from t1 where t1.b = ?");
    }

    @Test
    void nullStaysNull() {
        assertThat(SqlNormalizer.normalize(null)).isNull();
    }

    @Test
    void normalizationIsIdempotent() {
        String once = SqlNormalizer.normalize("select * from t where id = 7 and name = 'x'");

        assertThat(SqlNormalizer.normalize(once)).isEqualTo(once);
    }

    @Test
    void sameStatementWithDifferentLiteralsNormalizesIdentically() {
        String first = SqlNormalizer.normalize("select * from t where id = 1 and name = 'a'");
        String second = SqlNormalizer.normalize("select * from t where id = 99 and name = 'zzz'");

        assertThat(first).isEqualTo(second);
    }
}
