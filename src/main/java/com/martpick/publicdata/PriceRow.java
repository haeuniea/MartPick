package com.martpick.publicdata;

import java.time.LocalDate;

public record PriceRow(LocalDate inspectDay, long entpId, long goodId, int price) {
}
