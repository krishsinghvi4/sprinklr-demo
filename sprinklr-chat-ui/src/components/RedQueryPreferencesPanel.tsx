import { useEffect, useMemo, useState } from 'react'
import { Plus, Save } from 'lucide-react'
import {
  fetchRedQueryPreferences,
  updateRedQueryPreferences,
} from '../services/profileService'
import { MongoServerTypeConfig, RedQueryPreferences } from '../types/profile'
import {
  formatAllowlistLines,
  parseBulkAllowlistLines,
} from '../utils/parseBulkAllowlistLines'

interface RedQueryPreferencesPanelProps {
  connectionId: string
  initiallyConfigured: boolean
}

const emptyPreferences = (): RedQueryPreferences => ({
  elasticsearchServerTypes: [],
  mongoServerTypes: [],
})

function mongoCollectionsTextFromPreferences(
  preferences: RedQueryPreferences
): Record<string, string> {
  return Object.fromEntries(
    preferences.mongoServerTypes.map((entry) => [
      entry.serverType,
      formatAllowlistLines(entry.collectionNames),
    ])
  )
}

function buildDraftFromText(
  esTypesText: string,
  mongoCollectionsText: Record<string, string>,
  mongoServerTypes: MongoServerTypeConfig[]
): RedQueryPreferences {
  return {
    elasticsearchServerTypes: parseBulkAllowlistLines(esTypesText),
    mongoServerTypes: mongoServerTypes.map((entry) => ({
      serverType: entry.serverType,
      collectionNames: parseBulkAllowlistLines(mongoCollectionsText[entry.serverType] || ''),
    })),
  }
}

export default function RedQueryPreferencesPanel({
  connectionId,
  initiallyConfigured,
}: RedQueryPreferencesPanelProps) {
  const [saved, setSaved] = useState<RedQueryPreferences>(emptyPreferences())
  const [mongoServerTypes, setMongoServerTypes] = useState<MongoServerTypeConfig[]>([])
  const [esTypesText, setEsTypesText] = useState('')
  const [mongoCollectionsText, setMongoCollectionsText] = useState<Record<string, string>>({})
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [newMongoServerType, setNewMongoServerType] = useState('')

  const applyPreferencesToForm = (preferences: RedQueryPreferences) => {
    setSaved(preferences)
    setMongoServerTypes(preferences.mongoServerTypes)
    setEsTypesText(formatAllowlistLines(preferences.elasticsearchServerTypes))
    setMongoCollectionsText(mongoCollectionsTextFromPreferences(preferences))
  }

  const loadPreferences = async () => {
    setIsLoading(true)
    setError(null)
    try {
      const preferences = await fetchRedQueryPreferences(connectionId)
      applyPreferencesToForm(preferences)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load query allowlists.')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadPreferences()
  }, [connectionId])

  const parsedDraft = useMemo(
    () => buildDraftFromText(esTypesText, mongoCollectionsText, mongoServerTypes),
    [esTypesText, mongoCollectionsText, mongoServerTypes]
  )

  const hasUnsavedChanges = JSON.stringify(saved) !== JSON.stringify(parsedDraft)

  const handleSave = async () => {
    const emptyMongoEntry = parsedDraft.mongoServerTypes.find(
      (entry) => entry.collectionNames.length === 0
    )
    if (emptyMongoEntry) {
      setError(
        `Mongo serverType '${emptyMongoEntry.serverType}' must have at least one collection name.`
      )
      setSuccess(null)
      return
    }

    setIsSaving(true)
    setError(null)
    setSuccess(null)
    try {
      const updated = await updateRedQueryPreferences(connectionId, parsedDraft)
      applyPreferencesToForm(updated)
      setSuccess('Query allowlists saved.')
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to save query allowlists.')
    } finally {
      setIsSaving(false)
    }
  }

  const handleCancel = () => {
    applyPreferencesToForm(saved)
    setError(null)
    setSuccess(null)
  }

  const addMongoServerType = () => {
    const value = newMongoServerType.trim()
    if (!value || mongoServerTypes.some((entry) => entry.serverType === value)) return
    setMongoServerTypes([
      ...mongoServerTypes,
      { serverType: value, collectionNames: [] },
    ])
    setMongoCollectionsText((prev) => ({ ...prev, [value]: '' }))
    setNewMongoServerType('')
  }

  const removeMongoServerType = (serverType: string) => {
    setMongoServerTypes(mongoServerTypes.filter((entry) => entry.serverType !== serverType))
    setMongoCollectionsText((prev) => {
      const next = { ...prev }
      delete next[serverType]
      return next
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
            <p className="text-xs font-medium text-gray-700 mb-1">Elasticsearch server types</p>
            <p className="text-xs text-gray-500 mb-2">
              One server type per line (paste from spreadsheet OK).
            </p>
            <textarea
              value={esTypesText}
              onChange={(event) => setEsTypesText(event.target.value)}
              placeholder={'AUDIENCE_CONTAINER\nAUDIT_LOGS\nAD_ENTITY'}
              rows={4}
              className="w-full text-xs border border-gray-200 rounded-md px-2 py-1.5 font-mono resize-y min-h-[6rem]"
            />
            <p className="text-xs text-gray-500 mt-1">
              {parsedDraft.elasticsearchServerTypes.length} server type
              {parsedDraft.elasticsearchServerTypes.length === 1 ? '' : 's'}
            </p>
          </div>

          <div>
            <p className="text-xs font-medium text-gray-700 mb-2">Mongo server types & collections</p>
            <div className="space-y-3">
              {mongoServerTypes.map((entry) => {
                const collectionCount = parseBulkAllowlistLines(
                  mongoCollectionsText[entry.serverType] || ''
                ).length
                return (
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
                    <p className="text-xs text-gray-500 mb-2">
                      One collection name per line (paste from spreadsheet OK).
                    </p>
                    <textarea
                      value={mongoCollectionsText[entry.serverType] || ''}
                      onChange={(event) =>
                        setMongoCollectionsText((prev) => ({
                          ...prev,
                          [entry.serverType]: event.target.value,
                        }))}
                      placeholder="collectionOne&#10;collectionTwo"
                      rows={6}
                      className="w-full text-xs border border-gray-200 rounded-md px-2 py-1.5 font-mono resize-y min-h-[8rem]"
                    />
                    <p className="text-xs text-gray-500 mt-1">
                      {collectionCount} collection{collectionCount === 1 ? '' : 's'}
                    </p>
                  </div>
                )
              })}
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
