export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024
export const MAX_DIMENSION = 8192
export const MAX_PIXELS = 16_777_216

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]

function isPng(bytes) {
  return bytes.length >= PNG_SIGNATURE.length && PNG_SIGNATURE.every((byte, index) => bytes[index] === byte)
}

function isBmp(bytes) {
  return bytes.length >= 2 && bytes[0] === 0x42 && bytes[1] === 0x4d
}

function detectFormat(bytes) {
  if (isPng(bytes)) {
    return 'PNG'
  }
  if (isBmp(bytes)) {
    return 'BMP'
  }
  return null
}

function readDimensionsWithImageElement(file) {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(objectUrl)
      resolve({ width: image.naturalWidth, height: image.naturalHeight })
    }
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('The selected file could not be read as an image.'))
    }
    image.src = objectUrl
  })
}

async function readDimensions(file) {
  if (typeof createImageBitmap === 'function') {
    try {
      const bitmap = await createImageBitmap(file)
      try {
        return { width: bitmap.width, height: bitmap.height }
      } finally {
        bitmap.close()
      }
    } catch {
      // Fall back to an image element for formats the browser cannot bitmap.
    }
  }
  return readDimensionsWithImageElement(file)
}

export async function inspectImageFile(file) {
  if (!file) {
    throw new Error('Choose an image to continue.')
  }
  if (file.size === 0) {
    throw new Error('The selected image is empty.')
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    throw new Error('The image must be 5 MB or smaller.')
  }

  const header = new Uint8Array(await file.slice(0, 8).arrayBuffer())
  const format = detectFormat(header)
  if (!format) {
    throw new Error('The selected file is not a PNG or BMP image.')
  }

  const { width, height } = await readDimensions(file)
  if (width <= 0 || height <= 0) {
    throw new Error('The selected image has invalid dimensions.')
  }
  if (width > MAX_DIMENSION || height > MAX_DIMENSION || width * height > MAX_PIXELS) {
    throw new Error(
      `Image dimensions must be at most ${MAX_DIMENSION} pixels per side and ${MAX_PIXELS.toLocaleString()} total pixels.`,
    )
  }

  return { width, height, format }
}
