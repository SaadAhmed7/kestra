export function loopOver(item: unknown, predicate: (item: unknown) => boolean, result?: unknown[]): unknown[] {
    if (result === undefined) {
        result = []
    }

    if (predicate(item)) {
        result.push(item)
    }

    if (Array.isArray(item)) {
        item.flatMap(child => loopOver(child, predicate, result))
    } else if (item instanceof Object) {
        Object.entries(item as Record<string, unknown>).flatMap(([_key, value]) => {
            loopOver(value, predicate, result)
        })
    }

    return result
}

export function findTaskById(flow: unknown, taskId: string): {type?: string; id?: string; [key: string]: unknown} | undefined {
    const result = loopOver(flow, (value) => {
        if (value instanceof Object) {
            const obj = value as Record<string, unknown>
            if (obj["type"] !== undefined && obj["id"] === taskId) {
                return true
            }
        }

        return false
    })

    return result.length > 0 ? result[0] : undefined
}
