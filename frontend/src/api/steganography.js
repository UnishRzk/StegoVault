const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

const ENDPOINTS = {
  encode: '/api/steganography/encode',
  decode: '/api/steganography/decode',
  encodeEncrypted: '/api/steganography/encrypted/encode',
  decodeEncrypted: '/api/steganography/encrypted/decode',
}

export function isAbortError(error) {
  return error instanceof DOMException && error.name === 'AbortError'
}

async function postForm(path, formData, signal) {
  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      body: formData,
      signal,
    })
  } catch (error) {
    if (isAbortError(error)) {
      throw error
    }
    throw new Error('Unable to reach the server. Check that the backend is running and try again.')
  }

  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }

  return response
}

async function readErrorMessage(response) {
  try {
    const payload = await response.json()
    if (payload && typeof payload.message === 'string' && payload.message.trim()) {
      return payload.message
    }
  } catch {
    // Fall through to a status-based message; never surface raw response bodies.
  }

  if (response.status === 400) {
    return 'The request was rejected. Check the selected image and fields.'
  }
  if (response.status === 413) {
    return 'The image is too large or does not have enough capacity for this message.'
  }
  if (response.status === 422) {
    return 'The payload could not be processed.'
  }
  return 'The server could not complete the request. Please try again.'
}

function filenameFrom(response, fallback) {
  const header = response.headers.get('Content-Disposition')
  if (!header) {
    return fallback
  }
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header)
  if (!match) {
    return fallback
  }
  try {
    return decodeURIComponent(match[1])
  } catch {
    return fallback
  }
}

async function readEncodedImage(response, fallbackFilename) {
  const blob = await response.blob()
  return {
    blob,
    filename: filenameFrom(response, fallbackFilename),
  }
}

async function readDecodedMessage(response) {
  let payload
  try {
    payload = await response.json()
  } catch {
    throw new Error('The server returned an unexpected response.')
  }
  if (!payload || typeof payload.message !== 'string') {
    throw new Error('The server returned an unexpected response.')
  }
  return payload.message
}

export async function encodeImage({ image, message, signal }) {
  const formData = new FormData()
  formData.append('image', image)
  formData.append('message', message)
  const response = await postForm(ENDPOINTS.encode, formData, signal)
  return readEncodedImage(response, 'stego.png')
}

export async function decodeImage({ image, signal }) {
  const formData = new FormData()
  formData.append('image', image)
  const response = await postForm(ENDPOINTS.decode, formData, signal)
  return readDecodedMessage(response)
}

export async function encodeEncryptedImage({ image, message, password, signal }) {
  const formData = new FormData()
  formData.append('image', image)
  formData.append('message', message)
  formData.append('password', password)
  const response = await postForm(ENDPOINTS.encodeEncrypted, formData, signal)
  return readEncodedImage(response, 'stego-encrypted.png')
}

export async function decodeEncryptedImage({ image, password, signal }) {
  const formData = new FormData()
  formData.append('image', image)
  formData.append('password', password)
  const response = await postForm(ENDPOINTS.decodeEncrypted, formData, signal)
  return readDecodedMessage(response)
}
