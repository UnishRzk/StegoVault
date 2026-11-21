import { useEffect, useRef, useState } from 'react'
import {
  decodeEncryptedImage,
  decodeImage,
  encodeEncryptedImage,
  encodeImage,
  isAbortError,
} from './api/steganography.js'
import { inspectImageFile } from './utils/imageFile.js'

const PASSWORD_MIN_LENGTH = 8
const PASSWORD_MAX_LENGTH = 1024

function formatFileSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function BrandMark() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 3 5 6v5.5c0 4.2 2.8 7.6 7 9.5 4.2-1.9 7-5.3 7-9.5V6l-7-3Z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
      <rect
        x="9.25"
        y="10.25"
        width="5.5"
        height="4.5"
        rx="1"
        stroke="currentColor"
        strokeWidth="1.5"
      />
      <path
        d="M10.5 10.25V9a1.5 1.5 0 0 1 3 0v1.25"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  )
}

function UploadIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect
        x="3.5"
        y="4.5"
        width="17"
        height="15"
        rx="2.5"
        stroke="currentColor"
        strokeWidth="1.6"
      />
      <circle cx="8.5" cy="9.5" r="1.5" fill="currentColor" />
      <path
        d="m5 17 4.5-4.5 3.5 3.5 2.5-2.5L20 17"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function CheckCircleIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.7" />
      <path
        d="m8 12.5 2.5 2.5L16 9.5"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function DownloadIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 4v10m0 0 4-4m-4 4-4-4"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M5 17.5V19a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-1.5"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  )
}

function CopyIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect
        x="8.5"
        y="8.5"
        width="11"
        height="11"
        rx="2"
        stroke="currentColor"
        strokeWidth="1.6"
      />
      <path
        d="M15.5 8.5V6A1.5 1.5 0 0 0 14 4.5H6A1.5 1.5 0 0 0 4.5 6v8A1.5 1.5 0 0 0 6 15.5h2.5"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  )
}

function AlertIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.6" />
      <path d="M12 7.5v5.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      <circle cx="12" cy="16.5" r="1" fill="currentColor" />
    </svg>
  )
}

export default function App() {
  const [mode, setMode] = useState('encode')
  const [encrypted, setEncrypted] = useState(false)
  const [imageFile, setImageFile] = useState(null)
  const [imageInfo, setImageInfo] = useState(null)
  const [previewUrl, setPreviewUrl] = useState('')
  const [message, setMessage] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [checkingFile, setCheckingFile] = useState(false)
  const [copied, setCopied] = useState(false)
  const [dragActive, setDragActive] = useState(false)

  const fileInputRef = useRef(null)
  const requestRef = useRef(null)
  const previewUrlRef = useRef('')
  const downloadUrlRef = useRef('')
  const fileCheckRef = useRef(0)
  const mountedRef = useRef(true)
  const copyTimeoutRef = useRef(null)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      fileCheckRef.current += 1
      if (requestRef.current) {
        requestRef.current.abort()
        requestRef.current = null
      }
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current)
        previewUrlRef.current = ''
      }
      if (downloadUrlRef.current) {
        URL.revokeObjectURL(downloadUrlRef.current)
        downloadUrlRef.current = ''
      }
      if (copyTimeoutRef.current) {
        clearTimeout(copyTimeoutRef.current)
        copyTimeoutRef.current = null
      }
    }
  }, [])

  function revokePreviewUrl() {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current)
      previewUrlRef.current = ''
    }
    setPreviewUrl('')
  }

  function revokeDownloadUrl() {
    if (downloadUrlRef.current) {
      URL.revokeObjectURL(downloadUrlRef.current)
      downloadUrlRef.current = ''
    }
  }

  function cancelActiveRequest() {
    if (requestRef.current) {
      requestRef.current.abort()
      requestRef.current = null
    }
    setBusy(false)
  }

  function clearResult() {
    revokeDownloadUrl()
    setResult(null)
    setCopied(false)
  }

  function openFilePicker() {
    if (!fileInputRef.current) {
      return
    }
    fileInputRef.current.value = ''
    fileInputRef.current.click()
  }

  function handleModeChange(nextMode) {
    if (nextMode === mode) {
      return
    }
    cancelActiveRequest()
    clearResult()
    setError('')
    setShowPassword(false)
    setMode(nextMode)
  }

  function handleEncryptedChange(event) {
    const nextEncrypted = event.target.checked
    cancelActiveRequest()
    clearResult()
    setError('')
    setEncrypted(nextEncrypted)
    if (!nextEncrypted) {
      setPassword('')
      setShowPassword(false)
    }
  }

  async function processFile(file) {
    const checkToken = ++fileCheckRef.current

    cancelActiveRequest()
    clearResult()
    revokePreviewUrl()
    setError('')
    setImageInfo(null)
    setImageFile(file)

    if (!file) {
      setCheckingFile(false)
      return
    }

    setCheckingFile(true)
    try {
      const info = await inspectImageFile(file)
      if (!mountedRef.current || fileCheckRef.current !== checkToken) {
        return
      }
      setImageInfo(info)
      const objectUrl = URL.createObjectURL(file)
      previewUrlRef.current = objectUrl
      setPreviewUrl(objectUrl)
    } catch (validationError) {
      if (!mountedRef.current || fileCheckRef.current !== checkToken) {
        return
      }
      setError(
        validationError instanceof Error
          ? validationError.message
          : 'The selected file is not a valid image.',
      )
      setImageFile(null)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    } finally {
      if (mountedRef.current && fileCheckRef.current === checkToken) {
        setCheckingFile(false)
      }
    }
  }

  function handleFileChange(event) {
    const file = event.target.files?.[0] ?? null
    processFile(file)
  }

  function handleRemoveFile() {
    fileCheckRef.current += 1
    cancelActiveRequest()
    clearResult()
    revokePreviewUrl()
    setError('')
    setImageInfo(null)
    setImageFile(null)
    setCheckingFile(false)
    setDragActive(false)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  function handleDragEnter(event) {
    event.preventDefault()
    setDragActive(true)
  }

  function handleDragOver(event) {
    event.preventDefault()
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'copy'
    }
    setDragActive(true)
  }

  function handleDragLeave(event) {
    if (event.currentTarget.contains(event.relatedTarget)) {
      return
    }
    setDragActive(false)
  }

  function handleDrop(event) {
    event.preventDefault()
    setDragActive(false)
    const file = event.dataTransfer?.files?.[0] ?? null
    if (file) {
      processFile(file)
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (busy || checkingFile) {
      return
    }
    if (!imageFile || !imageInfo) {
      setError('Choose a valid PNG or BMP image first.')
      return
    }
    if (encrypted) {
      if (password.trim().length === 0) {
        setError('Enter a password.')
        return
      }
      if (password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH) {
        setError(
          `Password must be between ${PASSWORD_MIN_LENGTH} and ${PASSWORD_MAX_LENGTH} characters.`,
        )
        return
      }
    }

    cancelActiveRequest()
    clearResult()
    setError('')

    const controller = new AbortController()
    requestRef.current = controller
    setBusy(true)

    try {
      if (mode === 'encode') {
        const encoded = encrypted
          ? await encodeEncryptedImage({
              image: imageFile,
              message,
              password,
              signal: controller.signal,
            })
          : await encodeImage({ image: imageFile, message, signal: controller.signal })

        if (!mountedRef.current || controller.signal.aborted) {
          return
        }
        const objectUrl = URL.createObjectURL(encoded.blob)
        downloadUrlRef.current = objectUrl
        setResult({ kind: 'image', url: objectUrl, filename: encoded.filename })
      } else {
        const decoded = encrypted
          ? await decodeEncryptedImage({
              image: imageFile,
              password,
              signal: controller.signal,
            })
          : await decodeImage({ image: imageFile, signal: controller.signal })

        if (!mountedRef.current || controller.signal.aborted) {
          return
        }
        setResult({ kind: 'text', text: decoded })
      }
    } catch (requestError) {
      if (isAbortError(requestError) || controller.signal.aborted || !mountedRef.current) {
        return
      }
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'The request failed. Please try again.',
      )
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null
        if (mountedRef.current) {
          setBusy(false)
        }
      }
    }
  }

  async function handleCopy() {
    if (!result || result.kind !== 'text') {
      return
    }
    try {
      await navigator.clipboard.writeText(result.text)
      if (!mountedRef.current) {
        return
      }
      setCopied(true)
      if (copyTimeoutRef.current) {
        clearTimeout(copyTimeoutRef.current)
      }
      copyTimeoutRef.current = setTimeout(() => {
        if (mountedRef.current) {
          setCopied(false)
        }
      }, 2000)
    } catch {
      if (mountedRef.current) {
        setError('Unable to copy automatically. Select the message text and copy it manually.')
      }
    }
  }

  const submitLabel = mode === 'encode'
    ? (encrypted ? 'Encrypt and encode' : 'Encode image')
    : (encrypted ? 'Decrypt and decode' : 'Decode message')

  return (
    <div className="app">
      <header className="site-header">
        <div className="site-header-inner">
          <div className="brand">
            <span className="brand-mark">
              <BrandMark />
            </span>
            <div className="brand-text">
              <p className="brand-name">StegoVault</p>
              <p className="brand-subtitle">
                Hide and reveal secret messages inside PNG and BMP images.
              </p>
            </div>
          </div>
        </div>
      </header>

      <main className="main">
        <section className="workspace" aria-label="Steganography workspace">
          <div className="mode-switch" role="group" aria-label="Operation mode">
            <button
              type="button"
              aria-pressed={mode === 'encode'}
              onClick={() => handleModeChange('encode')}
            >
              <span className="mode-title">Encode</span>
              <span className="mode-caption">Hide a message</span>
            </button>
            <button
              type="button"
              aria-pressed={mode === 'decode'}
              onClick={() => handleModeChange('decode')}
            >
              <span className="mode-title">Decode</span>
              <span className="mode-caption">Reveal a message</span>
            </button>
          </div>

          <label className={`crypto-toggle${encrypted ? ' is-active' : ''}`} htmlFor="encrypted">
            <span className="crypto-text">
              <span className="crypto-title">
                Password protection
                <span className="crypto-badge">AES-GCM</span>
              </span>
              <span className="crypto-desc" id="encrypted-help">
                Encrypt the message before it is embedded. The same password is required to decode
                it.
              </span>
            </span>
            <span className="switch">
              <input
                id="encrypted"
                type="checkbox"
                role="switch"
                checked={encrypted}
                onChange={handleEncryptedChange}
                aria-describedby="encrypted-help"
              />
              <span className="switch-track" aria-hidden="true" />
            </span>
          </label>

          <form className="form" onSubmit={handleSubmit} aria-busy={busy}>
            <div className="field">
              <label
                className="field-label"
                htmlFor="image"
                onClick={(event) => {
                  event.preventDefault()
                  openFilePicker()
                }}
              >
                Source image
                <span className="label-hint">PNG or BMP · max 5 MB</span>
              </label>
              <input
                id="image"
                ref={fileInputRef}
                className="file-input"
                type="file"
                accept="image/png,image/bmp"
                onChange={handleFileChange}
                hidden
              />
              <div
                className={`upload-zone${dragActive ? ' is-dragging' : ''}`}
                onDragEnter={handleDragEnter}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
              >
                {!imageFile ? (
                  <button type="button" className="dropzone" onClick={openFilePicker}>
                    <span className="dropzone-icon" aria-hidden="true">
                      <UploadIcon />
                    </span>
                    <span className="dropzone-title">Drag and drop your image</span>
                    <span className="dropzone-subtitle">
                      or <span className="dropzone-browse">browse files</span>
                    </span>
                    <span className="dropzone-meta">
                      Max 8192 px per side · 16,777,216 total pixels
                    </span>
                  </button>
                ) : (
                  <div className="file-card">
                    <div className="file-thumb">
                      {previewUrl ? (
                        <img src={previewUrl} alt={`Preview of ${imageFile.name}`} />
                      ) : (
                        <span className="file-thumb-placeholder" aria-hidden="true">
                          <UploadIcon />
                        </span>
                      )}
                    </div>
                    <div className="file-details">
                      <p className="file-name" title={imageFile.name}>
                        {imageFile.name}
                      </p>
                      <p className="file-meta">
                        <span>{formatFileSize(imageFile.size)}</span>
                        {imageInfo && (
                          <>
                            <span className="dot" aria-hidden="true">·</span>
                            <span>{imageInfo.format}</span>
                            <span className="dot" aria-hidden="true">·</span>
                            <span>
                              {imageInfo.width} × {imageInfo.height} px
                            </span>
                          </>
                        )}
                      </p>
                      {checkingFile && <p className="hint">Checking image…</p>}
                    </div>
                    <div className="file-actions">
                      <button
                        type="button"
                        className="btn btn-ghost btn-sm"
                        onClick={openFilePicker}
                      >
                        Replace
                      </button>
                      <button
                        type="button"
                        className="btn btn-ghost btn-sm btn-danger-ghost"
                        onClick={handleRemoveFile}
                      >
                        Remove
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {mode === 'encode' && (
              <div className="field">
                <label className="field-label" htmlFor="message">
                  Message
                  <span className="label-hint">Hidden inside the image</span>
                </label>
                <textarea
                  id="message"
                  className="textarea"
                  rows={5}
                  value={message}
                  onChange={(event) => setMessage(event.target.value)}
                  placeholder="Type the message you want to hide…"
                />
              </div>
            )}

            {encrypted && (
              <div className="field">
                <label className="field-label" htmlFor="password">
                  Password
                </label>
                <div className="password-wrap">
                  <input
                    id="password"
                    className="text-input"
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    minLength={PASSWORD_MIN_LENGTH}
                    maxLength={PASSWORD_MAX_LENGTH}
                    autoComplete={mode === 'encode' ? 'new-password' : 'current-password'}
                    aria-describedby="password-hint"
                    required
                  />
                  <button
                    type="button"
                    className="password-toggle"
                    onClick={() => setShowPassword((visible) => !visible)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    aria-pressed={showPassword}
                  >
                    {showPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
                <p className="hint" id="password-hint">
                  Use {PASSWORD_MIN_LENGTH}–{PASSWORD_MAX_LENGTH} characters. The password is never
                  stored or logged by this page.
                </p>
              </div>
            )}

            {error && (
              <p className="error" role="alert">
                <AlertIcon />
                <span>{error}</span>
              </p>
            )}

            <div className="actions">
              <button
                type="submit"
                className={`btn btn-primary btn-lg${busy ? ' is-busy' : ''}`}
                disabled={busy || checkingFile || !imageFile || !imageInfo}
                aria-busy={busy}
              >
                {busy ? (
                  <>
                    <span className="spinner" aria-hidden="true" />
                    {mode === 'encode' ? 'Encoding…' : 'Decoding…'}
                  </>
                ) : (
                  submitLabel
                )}
              </button>
              {busy && (
                <button
                  type="button"
                  className="btn btn-secondary btn-lg"
                  onClick={cancelActiveRequest}
                >
                  Cancel
                </button>
              )}
            </div>
          </form>

          {result && (
            <section className="result" aria-live="polite" aria-labelledby="result-heading">
              <div className="result-header">
                <span className="result-icon" aria-hidden="true">
                  <CheckCircleIcon />
                </span>
                <div>
                  <h2 id="result-heading">
                    {result.kind === 'image' ? 'Encoded image ready' : 'Message decoded'}
                  </h2>
                  <p className="result-subtitle">
                    {result.kind === 'image'
                      ? 'Your message is hidden. Download the PNG to keep it.'
                      : 'The hidden message was extracted from the image.'}
                  </p>
                </div>
              </div>
              {result.kind === 'image' ? (
                <div className="result-body">
                  <div className="result-preview">
                    <img src={result.url} alt="Encoded stego image preview" />
                  </div>
                  <div className="result-actions">
                    <a
                      className="btn btn-primary"
                      href={result.url}
                      download={result.filename}
                    >
                      <DownloadIcon />
                      Download encoded PNG
                    </a>
                    <span className="result-filename" title={result.filename}>
                      {result.filename}
                    </span>
                  </div>
                </div>
              ) : (
                <div className="result-body">
                  <textarea
                    className="textarea decoded-text"
                    readOnly
                    rows={Math.min(10, Math.max(3, result.text.split('\n').length + 1))}
                    value={result.text}
                    aria-label="Decoded message"
                  />
                  <div className="result-actions">
                    <button type="button" className="btn btn-secondary" onClick={handleCopy}>
                      <CopyIcon />
                      {copied ? 'Copied' : 'Copy message'}
                    </button>
                  </div>
                </div>
              )}
            </section>
          )}
        </section>
      </main>

      <footer className="site-footer">
        <div className="site-footer-inner">
          <p>StegoVault uses LSB image steganography to embed messages directly in pixel data.</p>
          <p>Encrypted mode adds AES-256-GCM protection before embedding.</p>
        </div>
      </footer>
    </div>
  )
}
