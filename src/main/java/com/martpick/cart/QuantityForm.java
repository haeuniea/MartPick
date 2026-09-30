package com.martpick.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuantityForm(
		@NotNull @Min(GuestCart.MIN_QUANTITY) @Max(GuestCart.MAX_QUANTITY) Integer quantity) {
}
