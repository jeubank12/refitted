import { defineConfig } from "eslint/config";
import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import react from "eslint-plugin-react";
import typescriptEslint from "@typescript-eslint/eslint-plugin";
import _import from "eslint-plugin-import";
import { fixupPluginRules } from "@eslint/compat";
import globals from "globals";
import tsParser from "@typescript-eslint/parser";
import path from "node:path";
import { fileURLToPath } from "node:url";
import js from "@eslint/js";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
    allConfig: js.configs.all
});

export default defineConfig([{
    extends: [
        ...compat.extends("eslint:recommended"),
        ...compat.extends("plugin:react/recommended"),
        ...compat.extends("plugin:@typescript-eslint/recommended"),
        ...nextCoreWebVitals
    ],

    plugins: {
        react,
        "@typescript-eslint": typescriptEslint,
        import: fixupPluginRules(_import),
    },

    languageOptions: {
        globals: {
            ...globals.node,
        },

        parser: tsParser,
        ecmaVersion: "latest",
        sourceType: "module",
    },

    rules: {
        "react/react-in-jsx-scope": "off",
        "react-hooks/exhaustive-deps": "off",

        "no-multiple-empty-lines": ["error", {
            max: 1,
        }],

        "import/order": ["error", {
            groups: [
                "builtin",
                "external",
                "index",
                ["sibling", "parent", "internal"],
                "object",
                "type",
            ],

            pathGroups: [{
                pattern: "react",
                group: "builtin",
                position: "before",
            }, {
                pattern: "{next,next/**,next*,react-redux,@reduxjs/**,reselect}",
                group: "external",
                position: "before",
            }, {
                pattern: "{@(styles|features|store)/**,styles,features,store}",
                group: "internal",
            }],

            "newlines-between": "always",
            pathGroupsExcludedImportTypes: ["react"],
        }],
    },
}]);