package com.martpick.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

	@Id
	private String code;

	private String name;

	public Region(String code, String name) {
		this.code = code;
		this.name = name;
	}
}
