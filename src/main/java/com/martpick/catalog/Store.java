package com.martpick.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

	@Id
	private Long entpId;

	private String name;

	private String typeCode;

	private String areaCode;

	private String areaDetailCode;

	private String roadAddrBasic;

	private String roadAddrDetail;

	private String plmkAddrBasic;

	private String plmkAddrDetail;

	private boolean excluded;

	private String excludedReason;

	public Store(Long entpId, String name, String typeCode, String areaCode, String areaDetailCode,
			String roadAddrBasic, String roadAddrDetail, String plmkAddrBasic, String plmkAddrDetail,
			boolean excluded, String excludedReason) {
		this.entpId = entpId;
		this.name = name;
		this.typeCode = typeCode;
		this.areaCode = areaCode;
		this.areaDetailCode = areaDetailCode;
		this.roadAddrBasic = roadAddrBasic;
		this.roadAddrDetail = roadAddrDetail;
		this.plmkAddrBasic = plmkAddrBasic;
		this.plmkAddrDetail = plmkAddrDetail;
		this.excluded = excluded;
		this.excludedReason = excludedReason;
	}
}
