function initPage() {
	$("#submit-button").button();
}

function getRss(googlePlusUserId) {
	if (googlePlusUserId == "") {
		alert("you must fill googlePlusUserId");
	}
	else {
		window.location ="./rss/" + googlePlusUserId;
	}
	return false;
}


$(initPage);
