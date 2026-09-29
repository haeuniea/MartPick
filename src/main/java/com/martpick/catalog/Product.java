package com.martpick.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	private Long goodId;

	private String name;

	private String totalCnt;

	private String totalDivCode;

	public Product(Long goodId, String name, String totalCnt, String totalDivCode) {
		this.goodId = goodId;
		this.name = name;
		this.totalCnt = totalCnt;
		this.totalDivCode = totalDivCode;
	}
}
