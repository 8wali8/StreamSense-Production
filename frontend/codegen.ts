import type { CodegenConfig } from "@graphql-codegen/cli";

/**
 * Types are generated straight from the gateway's SDL, so the frontend can never drift from the
 * schema it talks to. `npm run codegen` rewrites src/graphql/generated.ts; CI runs `codegen:check`.
 */
const config: CodegenConfig = {
  schema: "../api-gateway/src/main/resources/graphql/*.graphqls",
  documents: ["src/graphql/*.ts"],
  generates: {
    "src/graphql/generated.ts": {
      plugins: ["typescript", "typescript-operations"],
      config: {
        skipTypename: true,
        enumsAsTypes: true,
        strictScalars: true,
        scalars: { ID: "string", Float: "number", Int: "number" },
      },
    },
  },
};

export default config;
