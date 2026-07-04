import { useEffect, useState } from 'react'
import { Plus, Save, X } from 'lucide-react'
import {
  fetchRedQueryPreferences,
  updateRedQueryPreferences,
} from '../services/profileService'
import { MongoServerTypeConfig, RedQueryPreferences } from '../types/profile'

interface RedQueryPreferencesPanelProps {
  connectionId: string
  initiallyConfigured: boolean
}

const emptyPreferences = (): RedQueryPreferences => ({
  elasticsearchServerTypes: [],
  mongoServerTypes: [],
})

export default function RedQueryPreferencesPanel({
  connectionId,
  initiallyConfigured,
}: RedQueryPreferencesPanelProps) {
  const [saved, setSaved] = useState<RedQueryPreferences>(emptyPreferences())
  const [draft, setDraft] = useState<RedQueryPreferences>(emptyPreferences())
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [newEsType, setNewEsType] = useState('')
  const [newMongoServerType, setNewMongoServerType] = useState('')
  const [newCollectionByServerType, setNewCollectionByServerType] = useState<Record<string, string>>({})

  const loadPreferences = async () => {
    setIsLoading(true)
    setError(null)
    try {
      const preferences = await fetchRedQueryPreferences(connectionId)
      setSaved(preferences)
      setDraft(preferences)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load query allowlists.')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadPreferences()
  }, [connectionId])

  const hasUnsavedChanges = JSON.stringify(saved) !== JSON.stringify(draft)

  const handleSave = async () => {
    setIsSaving(true)
    setError(null)
    setSuccess(null)
    try {
      const updated = await updateRedQueryPreferences(connectionId, draft)
      setSaved(updated)
      setDraft(updated)
      setSuccess('Query allowlists saved.')
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to save query allowlists.')
    } finally {
      setIsSaving(false)
    }
  }

  const handleCancel = () => {
    setDraft(saved)
    setError(null)
    setSuccess(null)
  }

  const addEsType = () => {
    const value = newEsType.trim()
    if (!value || draft.elasticsearchServerTypes.includes(value)) return
    setDraft({
      ...draft,
      elasticsearchServerTypes: [...draft.elasticsearchServerTypes, value],
    })
    setNewEsType('')
  }

  const removeEsType = (value: string) => {
    setDraft({
      ...draft,
      elasticsearchServerTypes: draft.elasticsearchServerTypes.filter((entry) => entry !== value),
    })
  }

  const addMongoServerType = () => {
    const value = newMongoServerType.trim()
    if (!value || draft.mongoServerTypes.some((entry) => entry.serverType === value)) return
    const next: MongoServerTypeConfig = { serverType: value, collectionNames: [] }
    setDraft({
      ...draft,
      mongoServerTypes: [...draft.mongoServerTypes, next],
    })
    setNewMongoServerType('')
  }

  const removeMongoServerType = (serverType: string) => {
    setDraft({
      ...draft,
      mongoServerTypes: draft.mongoServerTypes.filter((entry) => entry.serverType !== serverType),
    })
  }

  const addCollection = (serverType: string) => {
    const value = (newCollectionByServerType[serverType] || '').trim()
    if (!value) return
    setDraft({
      ...draft,
      mongoServerTypes: draft.mongoServerTypes.map((entry) => {
        if (entry.serverType !== serverType || entry.collectionNames.includes(value)) {
          return entry
        }
        return {
          ...entry,
          collectionNames: [...entry.collectionNames, value],
        }
      }),
    })
    setNewCollectionByServerType((prev) => ({ ...prev, [serverType]: '' }))
  }

  const removeCollection = (serverType: string, collectionName: string) => {
    setDraft({
      ...draft,
      mongoServerTypes: draft.mongoServerTypes.map((entry) => {
        if (entry.serverType !== serverType) return entry
        return {
          ...entry,
          collectionNames: entry.collectionNames.filter((name) => name !== collectionName),
        }
      }),
    })
  }

  return (
    <div className="mt-4 border-t border-gray-100 pt-4">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div>
          <h4 className="text-sm font-medium text-gray-900">Query allowlists</h4>
          <p className="text-xs text-gray-500 mt-1">
            Configure allowed server types and Mongo collections. The assistant uses these when
            running RED Elasticsearch or Mongo queries.
            {!initiallyConfigured && !isLoading && saved.elasticsearchServerTypes.length === 0
              && saved.mongoServerTypes.length === 0
              ? ' None configured yet.'
              : ''}
          </p>
        </div>
        <div className="flex gap-2 shrink-0">
          <button
            type="button"
            onClick={handleCancel}
            disabled={!hasUnsavedChanges || isSaving}
            className="px-2.5 py-1 text-xs border border-gray-200 rounded-md hover:bg-gray-50 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={!hasUnsavedChanges || isSaving}
            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            <Save className="w-3 h-3" />
            {isSaving ? 'Saving...' : 'Save'}
          </button>
        </div>
      </div>

      {isLoading ? (
        <p className="text-xs text-gray-500">Loading allowlists...</p>
      ) : (
        <div className="space-y-4">
          {error && (
            <p className="text-xs text-red-600">{error}</p>
          )}
          {success && (
            <p className="text-xs text-green-700">{success}</p>
          )}

          <div>
            <p className="text-xs font-medium text-gray-700 mb-2">Elasticsearch server types</p>
            <div className="flex flex-wrap gap-2 mb-2">
              {draft.elasticsearchServerTypes.map((serverType) => (
                <span
                  key={serverType}
                  className="inline-flex items-center gap-1 px-2 py-0.5 text-xs bg-gray-100 rounded-full"
                >
                  {serverType}
                  <button
                    type="button"
                    onClick={() => removeEsType(serverType)}
                    className="text-gray-500 hover:text-gray-800"
                    aria-label={`Remove ${serverType}`}
                  >
                    <X className="w-3 h-3" />
                  </button>
                </span>
              ))}
            </div>
            <div className="flex gap-2">
              <input
                type="text"
                value={newEsType}
                onChange={(event) => setNewEsType(event.target.value)}
                placeholder="e.g. AUDIENCE_CONTAINER"
                className="flex-1 text-xs border border-gray-200 rounded-md px-2 py-1.5"
              />
              <button
                type="button"
                onClick={addEsType}
                className="inline-flex items-center gap-1 px-2 py-1 text-xs border border-gray-200 rounded-md hover:bg-gray-50"
              >
                <Plus className="w-3 h-3" />
                Add
              </button>
            </div>
          </div>

          <div>
            <p className="text-xs font-medium text-gray-700 mb-2">Mongo server types & collections</p>
            <div className="space-y-3">
              {draft.mongoServerTypes.map((entry) => (
                <div key={entry.serverType} className="border border-gray-100 rounded-md p-3">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-medium text-gray-900">{entry.serverType}</span>
                    <button
                      type="button"
                      onClick={() => removeMongoServerType(entry.serverType)}
                      className="text-xs text-red-600 hover:text-red-700"
                    >
                      Remove
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-2 mb-2">
                    {entry.collectionNames.map((collectionName) => (
                      <span
                        key={collectionName}
                        className="inline-flex items-center gap-1 px-2 py-0.5 text-xs bg-gray-100 rounded-full"
                      >
                        {collectionName}
                        <button
                          type="button"
                          onClick={() => removeCollection(entry.serverType, collectionName)}
                          className="text-gray-500 hover:text-gray-800"
                          aria-label={`Remove ${collectionName}`}
                        >
                          <X className="w-3 h-3" />
                        </button>
                      </span>
                    ))}
                  </div>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={newCollectionByServerType[entry.serverType] || ''}
                      onChange={(event) =>
                        setNewCollectionByServerType((prev) => ({
                          ...prev,
                          [entry.serverType]: event.target.value,
                        }))}
                      placeholder="Collection name"
                      className="flex-1 text-xs border border-gray-200 rounded-md px-2 py-1.5"
                    />
                    <button
                      type="button"
                      onClick={() => addCollection(entry.serverType)}
                      className="inline-flex items-center gap-1 px-2 py-1 text-xs border border-gray-200 rounded-md hover:bg-gray-50"
                    >
                      <Plus className="w-3 h-3" />
                      Add
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <div className="flex gap-2 mt-3">
              <input
                type="text"
                value={newMongoServerType}
                onChange={(event) => setNewMongoServerType(event.target.value)}
                placeholder="e.g. PAID"
                className="flex-1 text-xs border border-gray-200 rounded-md px-2 py-1.5"
              />
              <button
                type="button"
                onClick={addMongoServerType}
                className="inline-flex items-center gap-1 px-2 py-1 text-xs border border-gray-200 rounded-md hover:bg-gray-50"
              >
                <Plus className="w-3 h-3" />
                Add server type
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
