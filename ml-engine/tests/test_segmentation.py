from PIL import Image

from ml_engine.segmentation import (
    RegionProposal,
    SamSegmenter,
    SegmentationConfig,
    create_segmenter,
)


def segmentation_config(**overrides):
    values = {
        "backend": "sam",
        "model_path": None,
        "model_version": "sam-vit-b",
        "confidence_threshold": 0.25,
        "iou_threshold": 0.5,
        "max_proposals": 20,
        "model_input_size": 640,
        "class_labels": ("segment",),
        "sam_model_type": "vit_b",
        "sam_checkpoint_url": "https://example.com/sam.pth",
        "sam_cache_dir": "/tmp/sam",
        "sam_device": "cpu",
        "sam_auto_download": False,
        "sam_points_per_side": 16,
        "min_area_ratio": 0.0005,
    }
    values.update(overrides)
    return SegmentationConfig(**values)


def test_region_proposal_clamps_to_normalized_frame_bounds():
    proposal = RegionProposal.from_bounds(
        label="candidate",
        confidence=1.5,
        x=0.9,
        y=-0.2,
        width=0.4,
        height=2.0,
        source="test",
    )

    assert proposal.confidence == 1.0
    assert proposal.x == 0.6
    assert proposal.y == 0.0
    assert proposal.width == 0.4
    assert proposal.height == 1.0
    assert proposal.area_ratio == 0.4


def test_default_segmentation_returns_no_proposals():
    image = Image.new("RGB", (4, 4), "white")

    assert create_segmenter(segmentation_config(backend="")).propose(image) == []


def test_heuristic_segmentation_returns_bounded_visual_region():
    image = Image.new("RGB", (8, 8), "white")
    for x in range(4):
        for y in range(8):
            image.putpixel((x, y), (0, 0, 0))

    proposals = create_segmenter(segmentation_config(backend="heuristic")).propose(image)

    assert len(proposals) == 1
    proposal = proposals[0]
    assert proposal.label == "visual-region"
    assert proposal.source == "heuristic"
    assert 0.0 <= proposal.x <= 1.0
    assert 0.0 <= proposal.y <= 1.0
    assert 0.0 < proposal.width <= 1.0
    assert 0.0 < proposal.height <= 1.0
    assert proposal.x + proposal.width <= 1.0
    assert proposal.y + proposal.height <= 1.0


def test_create_segmenter_supports_sam_backend():
    segmenter = create_segmenter(segmentation_config(backend="sam"))

    assert isinstance(segmenter, SamSegmenter)


def test_sam_segmenter_converts_masks_to_region_proposals():
    class FakeMaskGenerator:
        def generate(self, image):
            assert image.shape == (10, 20, 3)
            return [
                {"bbox": [2, 1, 6, 4], "predicted_iou": 0.91, "stability_score": 0.8},
                {"bbox": [0, 0, 2, 2], "predicted_iou": 0.2, "stability_score": 0.7},
            ]

    segmenter = SamSegmenter(
        segmentation_config(confidence_threshold=0.25, max_proposals=5),
        mask_generator=FakeMaskGenerator(),
    )

    proposals = segmenter.propose(Image.new("RGB", (20, 10), "white"))

    assert len(proposals) == 1
    assert proposals[0].source == "sam"
    assert proposals[0].label == "segment"
    assert proposals[0].confidence == 0.91
    assert proposals[0].x == 0.1
    assert proposals[0].y == 0.1
    assert proposals[0].width == 0.3
    assert proposals[0].height == 0.4


def test_sam_segmenter_returns_empty_without_checkpoint_when_download_disabled():
    segmenter = SamSegmenter(segmentation_config(sam_auto_download=False))

    assert segmenter.propose(Image.new("RGB", (4, 4), "white")) == []


def test_sam_segmenter_attempts_to_load_only_once():
    segmenter = SamSegmenter(segmentation_config(sam_auto_download=False))
    calls = []
    original = segmenter._build_mask_generator

    def counting_build():
        calls.append(1)
        return original()

    segmenter._build_mask_generator = counting_build
    image = Image.new("RGB", (4, 4), "white")

    assert segmenter.propose(image) == []
    assert segmenter.propose(image) == []
    assert len(calls) == 1
    assert segmenter.is_loaded() is False
