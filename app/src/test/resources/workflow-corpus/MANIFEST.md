# Workflow Compatibility Corpus

Real ComfyUI graph workflows used by `WorkflowCorpusTest` (Phase 89) to catch converter regressions.
Each fixture is converted with `GraphToApiConverter.convert()` against `object_info.json` and checked
by `ApiPromptValidator` (checks C1–C7 are described in `ApiPromptValidator.kt`).

## Sources

| Item | Source |
|---|---|
| `workflows/*.json` | `comfyui-workflow-templates-json` **0.1.95** (PyPI, via `comfyui-workflow-templates` 0.11.69), copied unchanged. MIT, see `LICENSE-workflow-templates.txt`. |
| `object_info.json` | Stock ComfyUI **v0.37.2** (commit `830232b856045ca2892833212d7771078a13edd5`), no custom nodes, run with `--cpu`; trimmed to the 132 node types the fixtures use. |
| Captured | 2026-09-25 |

`object_info.json` keeps the server's input order inside each node: the converter maps
`widgets_values` to inputs in that order, so never re-save it with sorted keys. Combo lists that hold
file names (models, input images) are emptied; `ApiPromptValidator` doesn't check file-name values.

No user workflows or prompts are ever added here.

## Fixtures

| File | Nodes | Features |
|---|---|---|
| `3d_hunyuan3d_multiview_to_model.json` | 18 nodes | bypass,load_image,note |
| `api_google_gemini.json` | 3 nodes | api_node,autogrow,dynamic_combo,linked_sub_input,load_image (added Phase 94) |
| `api_openai_gpt_image_2_image_edit.json` | 3 nodes | api_node,autogrow,dynamic_combo,linked_sub_input,load_image (added Phase 94) |
| `api_anthropic_claude_sonnet5.json` | 9 nodes | api_node,autogrow,linked_widget,load_image,subgraph |
| `api_bfl_flux1_expand_image.json` | 3 nodes | api_node,load_image |
| `api_kling_o3_i2v.json` | 5 nodes | api_node,autogrow,bypass,load_image,note,video_out |
| `api_luma_ray3_3_i2v.json` | 7 nodes | api_node,bypass,linked_widget,load_image,note,video_out |
| `audio_ace_step_1_5_checkpoint.json` | 11 nodes | linked_widget,note,primitive_node |
| `basic_datatype_conversion.json` | 20 nodes | autogrow |
| `flux_schnell.json` | 9 nodes | note |
| `hidream_e1_1.json` | 20 nodes | linked_widget,load_image,note,primitive_node,reroute |
| `hidream_e1_full.json` | 18 nodes | load_image,note,reroute |
| `hunyuan_video_text_to_video.json` | 19 nodes | mute,note,video_out |
| `image_boogu_image_0_1_turbo_t2i.json` | 12 nodes | linked_widget,note,subgraph |
| `image_mage_flow_t2i_int8.json` | 11 nodes | autogrow,linked_widget,note,subgraph |
| `image_qwen_Image_2512_controlnet.json` | 30 nodes | linked_widget,load_image,note,reroute,subgraph |
| `image_sdxl_simple.json` | 8 nodes | note |
| `image_to_video_wan.json` | 15 nodes | load_image,note,video_out |
| `sd3.5_simple_example.json` | 8 nodes | note |
| `template_image_speech_to_video.json` | 69 nodes | autogrow,bypass,linked_widget,load_image,mute,note,reroute,subgraph,video_out |
| `templates-character_sheet.json` | 9 nodes | load_image,reroute |
| `text_to_video_wan.json` | 11 nodes | video_out |
| `utility-gan_upscaler.json` | 7 nodes | linked_widget,note,video_out |
| `utility_birefnet_remove_background.json` | 9 nodes | linked_widget,load_image,note,subgraph |
| `utility_depth_anything3_image_depth_estimation.json` | 9 nodes | autogrow,linked_widget,load_image,note,subgraph |
| `utility_depth_anything3_video_depth_estimation.json` | 12 nodes | autogrow,bypass,linked_widget,note,subgraph,video_out |
| `utility_image_stitch.json` | 10 nodes | load_image,note |
| `utility_topaz_illustration_upscale.json` | 8 nodes | linked_widget,load_image,note,primitive_node |
| `video_kandinsky5_t2v.json` | 14 nodes | note,subgraph,video_out |
| `video_ltx2_depth_to_video.json` | 107 nodes | autogrow,bypass,linked_widget,load_image,note,reroute,subgraph,video_out |
| `video_wan2.1_fun_camera_v1.1_1.3B.json` | 17 nodes | bypass,linked_widget,load_image,note,video_out |
| `video_wan2_2_5B_ti2v.json` | 13 nodes | bypass,load_image,note,video_out |

Coverage: 10 subgraph, 15 linked widget, 10 autogrow, 8 bypass, 2 mute, 6 Reroute, 3 PrimitiveNode,
20 LoadImage, 12 video output, 9 API node; dynamic combos (incl. nested SaveVideo `format`, sub-inputs
linked or autogrow inside an option) in most fixtures; plain text-to-image: `image_sdxl_simple`, `flux_schnell`,
`sd3.5_simple_example`; large graph: `video_ltx2_depth_to_video` (107 nodes).

## Known failures

`known-failures.json` lists fixtures that fail today, the checks they fail, why, and the roadmap
phase meant to fix them. The test enforces the list both ways:

- a fixture not in the list that fails any check is a **regression**;
- a listed fixture whose failing checks change (including passing entirely) is **stale**.

When a converter fix makes a fixture pass, delete its entry (or narrow `checks`) in the same change.

## Regenerating

```bash
pip download --no-deps comfyui-workflow-templates-json==0.1.95 -d wt && unzip -q wt/*.whl -d wt/x
python scripts/corpus/select_fixtures.py wt/x/comfyui_workflow_templates_json/templates   # survey features
# copy the chosen files into workflows/, start ComfyUI (python main.py --cpu), then:
python scripts/corpus/export_object_info.py --url http://127.0.0.1:8188 \
    --fixtures app/src/test/resources/workflow-corpus/workflows \
    --out app/src/test/resources/workflow-corpus/object_info.json
```

To add a fixture, check `select_fixtures.py --object-info object_info.json` reports no missing node
types, re-export `object_info.json` if it does, and run the tests.
