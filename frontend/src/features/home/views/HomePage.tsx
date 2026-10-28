import {
  ArrowRight,
  Clock3,
  Network,
  Plus,
} from 'lucide-react'
import {
  Box,
  Button,
  Container,
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  ThemeIcon,
  Title,
  UnstyledButton,
} from '@mantine/core'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listConversations } from '../../../shared/api'
import { Brand } from '../../../shared/components/Brand'
import {
  CONVERSATION_KIND_MODELING,
  type Conversation,
} from '../../../shared/types'
import { HOME_COPY, HOME_FEATURES } from '../lib/constants'

export function HomePage() {
  const navigate = useNavigate()
  const [conversations, setConversations] = useState<Conversation[]>([])

  useEffect(() => {
    void listConversations(CONVERSATION_KIND_MODELING)
      .then(setConversations)
      .catch(() => setConversations([]))
  }, [])

  return (
    <Box component="main" className="home-page">
      <Container size="lg" px="md" py="xl">
        <Brand icon={<Network size={20} />} title={HOME_COPY.brand} subtitle={HOME_COPY.subtitle} />

        <Box className="home-content">
          <Stack className="home-intro" gap="md">
            <Text className="eyebrow">{HOME_COPY.eyebrow}</Text>
            <Title order={1}>{HOME_COPY.title}</Title>
            <Text className="home-intro__copy">{HOME_COPY.intro}</Text>
          </Stack>

          <SimpleGrid className="feature-grid" cols={{ base: 1, sm: 2 }} spacing="md" mt="xl">
            {HOME_FEATURES.map((feature) => {
              const Icon = feature.icon
              return (
                <UnstyledButton
                  type="button"
                  className="feature-card"
                  key={feature.id}
                  onClick={() => navigate(feature.path)}
                >
                  <ThemeIcon className="feature-card__icon" size={52} radius="lg" variant="light" color="brand">
                    <Icon size={26} />
                  </ThemeIcon>
                  <Stack className="feature-card__body" gap={6} align="flex-start">
                    <Text component="strong" className="feature-card__title">{feature.title}</Text>
                    <Text component="span" className="feature-card__description">{feature.description}</Text>
                  </Stack>
                  <Group className="feature-card__action" gap={7} wrap="nowrap">
                    {HOME_COPY.enter}
                    <ArrowRight size={17} />
                  </Group>
                </UnstyledButton>
              )
            })}
          </SimpleGrid>

          <Box className="recent-section">
            <Group className="section-heading" justify="space-between">
              <Group gap={8}>
                <Clock3 size={17} />
                <Text component="h2" fw={700} size="sm">{HOME_COPY.recentTitle}</Text>
              </Group>
              <Button
                type="button"
                variant="subtle"
                size="xs"
                leftSection={<Plus size={15} />}
                onClick={() => navigate('/modeling')}
              >
                {HOME_COPY.create}
              </Button>
            </Group>
            {conversations.length ? (
              <SimpleGrid className="recent-list" cols={{ base: 1, sm: 2, lg: 3 }} spacing="sm" mt="sm">
                {conversations.slice(0, 6).map((conversation) => (
                  <UnstyledButton
                    type="button"
                    className="recent-item"
                    key={conversation.id}
                    onClick={() => navigate(`/modeling/${conversation.id}`)}
                  >
                    <Text component="span" size="sm" truncate>
                      {conversation.title || '未命名对话'}
                    </Text>
                    <ArrowRight size={15} />
                  </UnstyledButton>
                ))}
              </SimpleGrid>
            ) : (
              <Paper withBorder radius="md" p="md" mt="sm">
                <Text size="sm" c="dimmed">{HOME_COPY.recentEmpty}</Text>
              </Paper>
            )}
          </Box>
        </Box>
      </Container>
    </Box>
  )
}
