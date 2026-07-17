/** @type {import('stylelint').Config} */
export default {
    extends: [
        "stylelint-config-recommended-scss",
        "stylelint-config-recommended-vue/scss",
    ],
    plugins: [
        "./plugins/lint-custom-properties.mjs",
    ],
    // Golden-rule enforcement (ui/AGENTS.md) starts at "warning" severity
    // while the existing violations are burned down; flip each rule to
    // "error" once its category reaches zero.
    rules: {
        // Golden rule 2: colors come from --ks-* tokens, never hex.
        "color-no-hex": [true, {severity: "warning"}],
        "no-descending-specificity": null,
        "ks/custom-property-pattern-usage": [
            /(?<=ks-)/,
            {
                severity: "warning",
                message: (prop) =>  `"${prop}" is not allowed. Try to use "--ks" prefixed custom properties`,
            },
        ],
        "scss/no-global-function-names": null,
        // Vue's :deep()/:global()/:slotted() are valid in .scss partials
        // imported into scoped SFC blocks; the vue config only whitelists
        // them for *.vue, and this root-level entry shadows that override.
        // (:deep is still flagged by the golden-rule ban below.)
        "selector-pseudo-class-no-unknown": [true, {ignorePseudoClasses: ["deep", "global", "slotted"]}],
        // Pre-existing violations of the recommended-scss preset, downgraded
        // to warnings until burned down — flip back to error at zero.
        "declaration-property-value-keyword-no-deprecated": [true, {severity: "warning"}],
        "property-no-deprecated": [true, {severity: "warning"}],
        "scss/dollar-variable-no-missing-interpolation": [true, {severity: "warning"}],
        "declaration-block-no-duplicate-properties": [true, {severity: "warning"}],
        "declaration-block-no-shorthand-property-overrides": [true, {severity: "warning"}],
        "scss/load-partial-extension": ["never", {severity: "warning"}],
        // Golden rule 4: no :deep() — expose a prop, slot, or CSS variable
        // in the design system instead.
        "selector-pseudo-class-disallowed-list": [
            ["deep"],
            {
                severity: "warning",
                message: ":deep() is forbidden in feature code — expose a prop, slot, or CSS variable in the design system instead (ui/AGENTS.md golden rule 4).",
            },
        ],
        "selector-pseudo-element-disallowed-list": [
            ["v-deep"],
            {
                severity: "warning",
                message: "::v-deep is forbidden in feature code — expose a prop, slot, or CSS variable in the design system instead (ui/AGENTS.md golden rule 4).",
            },
        ],
        // Golden rule 7: never override Element Plus (.kel-*) classes —
        // extend the Ks* component in the design system instead.
        "selector-disallowed-list": [
            [/\.kel-/],
            {
                severity: "warning",
                splitList: true,
                message: "Overriding Element Plus (.kel-*) classes is forbidden — extend the Ks* component in the design system instead (ui/AGENTS.md golden rule 7).",
            },
        ],
        // Golden rules 5 & 6: no SCSS $variables in feature code, and no
        // hardcoded px for spacing/radii/font sizes — use --ks-* tokens
        // (e.g. the --ks-spacing-* scale).
        "declaration-property-value-disallowed-list": [
            {
                "/^(padding|margin|gap|row-gap|column-gap|border-radius|font-size)/": [/\d+px/],
                "/.*/": [/\$[a-zA-Z]/],
            },
            {
                severity: "warning",
                message: "Use --ks-* design tokens (e.g. --ks-spacing-*) instead of SCSS $variables or hardcoded px values (ui/AGENTS.md golden rules 5 & 6).",
            },
        ],
        "scss/no-dollar-variables": [
            true,
            {
                severity: "warning",
                message: "SCSS $variables are forbidden in feature code — use --ks-* design tokens (ui/AGENTS.md golden rule 5).",
            },
        ],
    },
}
