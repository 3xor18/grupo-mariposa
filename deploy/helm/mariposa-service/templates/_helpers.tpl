{{- define "mariposa.name" -}}
{{- default .Release.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "mariposa.imageTag" -}}
{{- required "image.tag is required (pass --set image.tag=<git sha>)" .Values.image.tag -}}
{{- end -}}

{{- define "mariposa.image" -}}
{{- $repository := required "image.repository is required" .Values.image.repository -}}
{{- printf "%s:%s" $repository (include "mariposa.imageTag" .) -}}
{{- end -}}

{{- define "mariposa.configChecksum" -}}
{{- $inputs := dict "env" .Values.env "externalSecret" .Values.externalSecret -}}
{{- toJson $inputs | sha256sum -}}
{{- end -}}

{{- define "mariposa.labels" -}}
app.kubernetes.io/name: {{ include "mariposa.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ include "mariposa.imageTag" . | quote }}
app.kubernetes.io/part-of: grupo-mariposa
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "mariposa.selectorLabels" -}}
app.kubernetes.io/name: {{ include "mariposa.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
