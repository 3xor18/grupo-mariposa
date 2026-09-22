{{- define "mariposa.name" -}}
{{- default .Release.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "mariposa.labels" -}}
app.kubernetes.io/name: {{ include "mariposa.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Values.image.tag | quote }}
app.kubernetes.io/part-of: grupo-mariposa
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "mariposa.selectorLabels" -}}
app.kubernetes.io/name: {{ include "mariposa.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
