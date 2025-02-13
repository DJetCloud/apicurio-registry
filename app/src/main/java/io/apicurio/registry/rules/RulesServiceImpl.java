package io.apicurio.registry.rules;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.LazyContentList;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements the {@link RulesService} interface.
 *
 */
@ApplicationScoped
public class RulesServiceImpl implements RulesService {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RuleExecutorFactory factory;

    @Inject
    RulesProperties rulesProperties;

    @Inject
    ArtifactTypeUtilProviderFactory providerFactory;

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRules(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.rules.RuleApplicationType, java.util.List, java.util.Map)
     */
    @Override
    public void applyRules(String groupId, String artifactId, String artifactType, ContentHandle artifactContent,
                           RuleApplicationType ruleApplicationType, List<ArtifactReference> references,
                           Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        @SuppressWarnings("unchecked")
        List<RuleType> rules = Collections.EMPTY_LIST;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            rules = storage.getArtifactRules(groupId, artifactId);
        }
        LazyContentList currentContent = null;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            currentContent = new LazyContentList(storage, storage.getEnabledArtifactContentIds(groupId, artifactId));
        } else {
            currentContent = new LazyContentList(storage, Collections.emptyList());
        }

        applyGlobalAndArtifactRules(groupId, artifactId, artifactType, currentContent, artifactContent, rules, references, resolvedReferences);
    }

    private void applyGlobalAndArtifactRules(String groupId, String artifactId, String artifactType,
                                             List<ContentHandle> currentArtifactContent, ContentHandle updatedArtifactContent,
                                             List<RuleType> artifactRules, List<ArtifactReference> references, Map<String, ContentHandle> resolvedReferences) {

        Map<RuleType, RuleConfigurationDto> globalOrArtifactRulesMap = artifactRules.stream()
                .collect(Collectors.toMap(ruleType -> ruleType, ruleType -> storage.getArtifactRule(groupId, artifactId, ruleType)));

        if (globalOrArtifactRulesMap.isEmpty()) {
            List<RuleType> globalRules = storage.getGlobalRules();
            globalOrArtifactRulesMap = globalRules.stream()
                    .collect(Collectors.toMap(ruleType -> ruleType, storage::getGlobalRule));

            // Add any default global rules to the map (after filtering out any global rules from artifactStore)
            Map<RuleType, RuleConfigurationDto> filteredDefaultGlobalRulesMap = rulesProperties.getFilteredDefaultGlobalRules(globalRules).stream()
                    .collect(Collectors.toMap(ruleType -> ruleType, rulesProperties::getDefaultGlobalRuleConfiguration));
            globalOrArtifactRulesMap.putAll(filteredDefaultGlobalRulesMap);
        }

        if (globalOrArtifactRulesMap.isEmpty()) {
            return;
        }

        for (RuleType ruleType : globalOrArtifactRulesMap.keySet()) {
            applyRule(groupId, artifactId, artifactType, currentArtifactContent, updatedArtifactContent, ruleType,
                    globalOrArtifactRulesMap.get(ruleType).getConfiguration(), references, resolvedReferences);
        }
    }

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRule(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.types.RuleType, java.lang.String, io.apicurio.registry.rules.RuleApplicationType, java.util.List, java.util.Map)
     */
    @Override
    public void applyRule(String groupId, String artifactId, String artifactType, ContentHandle artifactContent,
                          RuleType ruleType, String ruleConfiguration, RuleApplicationType ruleApplicationType,
                          List<ArtifactReference> references, Map<String, ContentHandle> resolvedReferences)
            throws RuleViolationException {
        LazyContentList currentContent = null;
        if (ruleApplicationType == RuleApplicationType.UPDATE) {
            currentContent = new LazyContentList(storage, storage.getEnabledArtifactContentIds(groupId, artifactId));
        }
        applyRule(groupId, artifactId, artifactType, currentContent, artifactContent, ruleType, ruleConfiguration,
                references, resolvedReferences);
    }

    /**
     * Applies a single rule.  Throws an exception if the rule is violated.
     */
    private void applyRule(String groupId, String artifactId, String artifactType, List<ContentHandle> currentContent,
                           ContentHandle updatedContent, RuleType ruleType, String ruleConfiguration,
                           List<ArtifactReference> references, Map<String, ContentHandle> resolvedReferences) {
        RuleExecutor executor = factory.createExecutor(ruleType);
        RuleContext context = new RuleContext(groupId, artifactId, artifactType, ruleConfiguration, currentContent,
                updatedContent, references, resolvedReferences);
        executor.execute(context);
    }

    /**
     * @see io.apicurio.registry.rules.RulesService#applyRules(java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.content.ContentHandle, java.util.List, java.util.Map)
     */
    @Override
    public void applyRules(String groupId, String artifactId, String artifactVersion, String artifactType,
                           ContentHandle updatedContent, List<ArtifactReference> references,
                           Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        StoredArtifactDto versionContent = storage.getArtifactVersion(groupId, artifactId, artifactVersion);
        applyGlobalAndArtifactRules(groupId, artifactId, artifactType, Collections.singletonList(versionContent.getContent()),
                updatedContent, storage.getArtifactRules(groupId, artifactId), references, resolvedReferences);
    }

    @Override
    public void applyRulesCompat(String groupId, String artifactId, String artifactVersion, String artifactType,
                                 ContentHandle updatedContent, List<ArtifactReference> references,
                                 Map<String, ContentHandle> resolvedReferences) throws RuleViolationException {
        ArtifactTypeUtilProvider artifactTypeProvider = providerFactory.getArtifactTypeProvider(artifactType);
        ContentCanonicalizer contentCanonicalizer = artifactTypeProvider.getContentCanonicalizer();
        StoredArtifactDto versionContent = storage.getArtifactVersion(groupId, artifactId, artifactVersion);
        applyGlobalAndArtifactRules(groupId, artifactId, artifactType,
                Collections.singletonList(contentCanonicalizer.canonicalize(versionContent.getContent(), Map.of())),
                updatedContent, storage.getArtifactRules(groupId, artifactId), references, resolvedReferences);
    }
}
