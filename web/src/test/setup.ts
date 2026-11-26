import 'fake-indexeddb/auto'
import '@testing-library/jest-dom/vitest'

if (typeof HTMLDialogElement !== 'undefined') {
  const dialog = HTMLDialogElement.prototype
  if (!dialog.showModal) {
    dialog.showModal = function () {
      this.setAttribute('open', '')
    }
  }
  if (!dialog.close) {
    dialog.close = function (returnValue = '') {
      this.returnValue = returnValue
      this.removeAttribute('open')
      this.dispatchEvent(new Event('close'))
    }
  }
}
