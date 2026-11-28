import { apiRequest } from './auth'

export type MediaStatus = 'PROBE_PENDING' | 'READY' | 'FAILED'
export type MediaSort = 'CREATED_DESC' | 'NAME_ASC'

export interface MediaPolicy {
  policyVersion: string
  maxFileBytes: number
  maxVideoDurationMs: number
  chunkSizeBytes: number
  maxConcurrentParts: number
  maxActiveUploads: number
  sessionTtlSeconds: number
  allowedExtensions: string[]
  allowedVideoCodecs: string[]
  allowedAudioCodecs: string[]
  playbackTicketTtlSeconds: number
}

export interface MediaFailure {
  code: string
  message: string
}

export interface DeletionImpact {
  canDelete: boolean
  blockers: string[]
}

export interface MediaSummary {
  mediaId: string
  displayName: string
  sizeBytes: number
  durationMs: number | null
  status: MediaStatus
  version: number
  createdAt: string
  allowedActions: string[]
}

export interface MediaDetail extends MediaSummary {
  originalName: string
  container: string | null
  videoCodec: string | null
  audioCodec: string | null
  width: number | null
  height: number | null
  failure: MediaFailure | null
  processingJobId: string | null
  deletion: DeletionImpact
}

export interface MediaPage {
  items: MediaSummary[]
  page: number
  pageSize: number
  total: number
}

export function fetchMediaPolicy(): Promise<MediaPolicy> {
  return apiRequest<MediaPolicy>('/api/media/policy')
}

export function fetchMediaList(input: {
  q?: string
  status?: MediaStatus
  page: number
  pageSize: number
  sort: MediaSort
}): Promise<MediaPage> {
  const query = new URLSearchParams({
    page: String(input.page),
    pageSize: String(input.pageSize),
    sort: input.sort,
  })
  if (input.q) query.set('q', input.q)
  if (input.status) query.set('status', input.status)
  return apiRequest<MediaPage>(`/api/media?${query.toString()}`)
}

export function fetchMediaDetail(mediaId: string): Promise<MediaDetail> {
  return apiRequest<MediaDetail>(`/api/media/${encodeURIComponent(mediaId)}`)
}