import type {extendMoment} from "moment-range"
import type * as filters from "./utils/filters"

declare module "@vue/runtime-core" {
    interface ComponentCustomProperties {
        $moment: ReturnType<typeof extendMoment>
        $filters: typeof filters
    }
}
