// Based on GPLv3 code from deltachat-android
// https://github.com/deltachat/deltachat-android/blob/master/res/raw/webxdc.js

window.webxdc = (() => {
	let setUpdateListenerPromise = null
	var update_listener = () => {};
	var last_serial = 0;

	window.__webxdcUpdate = () => {
		var updates = JSON.parse(InternalJSApi.getStatusUpdates(last_serial));
		updates.forEach((update) => {
				update_listener(update);
				last_serial = update.serial;
		});
		if (setUpdateListenerPromise) {
			setUpdateListenerPromise();
			setUpdateListenerPromise = null;
		}
	};

	return {
		selfAddr: InternalJSApi.selfAddr(),

		selfName: InternalJSApi.selfName(),

		setUpdateListener: (cb, serial) => {
				last_serial = typeof serial === "undefined" ? 0 : parseInt(serial);
				update_listener = cb;
				var promise = new Promise((res, _rej) => {
					setUpdateListenerPromise = res;
				});
				window.__webxdcUpdate();
				return promise;
		},

		sendUpdate: (payload, descr) => {
			InternalJSApi.sendStatusUpdate(JSON.stringify(payload), descr);
		},
	};
})();
