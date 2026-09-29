package com.martpick.publicdata;

public record StoreRow(
		long entpId,
		String name,
		String typeCode,
		String areaCode,
		String areaDetailCode,
		String roadAddrBasic,
		String roadAddrDetail,
		String plmkAddrBasic,
		String plmkAddrDetail) {
}
